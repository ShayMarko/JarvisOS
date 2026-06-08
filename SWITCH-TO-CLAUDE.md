# Switching Jarvis → Claude / OpenAI — switch-day runbook

The framework is provider-agnostic and switch-ready: `ProviderSwitchingLanguageModel` picks the backing
provider **per call** from `jarvis.ai.provider`, rebuilds the adapter when a key appears, falls back to the
offline mock/Ollama only on transient/network errors, and surfaces real auth errors instead of hiding them.
No rewiring needed — but do the steps below so it "just works."

## 0. Before you flip the switch (do these first)
- [ ] **Rotate the Discord bot token** — it leaked in a transcript during the build. Regenerate it in the
      Discord dev portal and update `config/application.yml`. Treat the old one as compromised.
- [ ] **Set real model IDs** (see §2 — this is the #1 switch-breaker).
- [ ] Confirm the monthly USD cap is what you want (§4 — defaults to **$80/mo**, calibrated).
- [ ] Leave the **autonomous Coordinator OFF** (`JARVIS_COORDINATOR_ENABLED=false`, the default). Don't arm
      it until the manual funnel has earned and the brain is on Opus.

## 1. Set the keys + provider
- Keys are read from **env vars at boot** (bound in `application.yml`), NOT settable from the UI:
  - `export ANTHROPIC_API_KEY=sk-ant-...`   (and/or `export OPENAI_API_KEY=sk-...`)
- Pick the active provider:
  - boot env: `export JARVIS_AI_PROVIDER=claude`  (or `openai`), **then restart the backend**, **or**
  - runtime, no restart: `POST /api/settings/provider {"provider":"claude"}` (key must already be in env).
- Verify: `GET /api/settings` → `hasAnthropicKey:true`, `provider:"claude"`.
- Keep **Ollama running** even after switching: the Privacy Router swaps sensitive prompts to the LOCAL
  model, embeddings/KB use it, and it's the fallback if a paid call fails. Don't shut it down.

## 2. ⚠️ Landmine A (THE big one) — set real model IDs, or you silently run on Ollama
The Anthropic tiers in `application.yml` (`jarvis.ai`) default to **placeholder names**:
`model: claude-opus-4-8`, `claude-standard-model: claude-sonnet-4-8` — these are **NOT real API model IDs**.
(`planner-model-claude: claude-3-5-haiku-latest` IS real.)
- **Why it's sneaky:** a bad model id is not an auth error, so it doesn't shout — the call fails and
  **falls back to local Ollama**. You'll *think* you're on Opus while actually running qwen. No error, just
  quietly wrong (and cheap, which hides it).
- **Fix:** get the exact current IDs from your Anthropic console / `/v1/models` and set:
  - `JARVIS_AI_MODEL` (HEAVY/main — pick a vision-capable one; Claude 4-class models are),
  - `JARVIS_AI_CLAUDE_STANDARD` (STANDARD), `JARVIS_AI_PLANNER_CLAUDE` (LIGHT/planner).
  - OpenAI equivalents: `JARVIS_AI_OPENAI_MODEL`, `JARVIS_AI_PLANNER_OPENAI` (prefer `gpt-4o` over `-mini` for vision).
- **Confirm you're really on Opus:** after the switch, ask a question and check the agent-run trace shows
  `model = <your real opus id>`, NOT the ollama model. (See §6.)

## 3. ✅ Landmine B (max-tokens) — already handled
`jarvis.ai.max-tokens` is now **8192** (was 1024, which truncated big builds). No action needed unless you
want it higher for very large single files. Local Ollama is separately capped by `ollama-num-predict: 8192`.

## 4. ⚠️ Landmine C — spend caps (console billing is pay-per-token, unlike the flat CLI sub)
Two governors, both already wired (governor + kill-switch + HUD meter enforce them):
- **Monthly USD cap** (the main one): `jarvis.ai.monthly-budget-usd` defaults to **$80**. Prices in
  `ModelCatalog` are calibrated to real Anthropic rates (Opus $15/$75, Sonnet $3/$15, Haiku $0.80/$4 per M),
  so the cap enforces correctly **once §2's model IDs are real** (cost is keyed by model id). At ≥80% it
  downshifts to free Ollama; at 100% paid calls pause till next month. Change via
  `JARVIS_AI_MONTHLY_BUDGET_USD` or `POST /api/settings/budget {"monthlyBudgetUsd":80}`.
- **Daily token cap** (optional belt-and-braces): `JARVIS_AI_DAILY_TOKEN_BUDGET=2000000` (0 = off).

## 5. What lights up automatically on the switch (no action)
- **Vision** — `describe_image` / `see_screen` start returning real descriptions (were "needs a key").
- **Privacy Router** — now ACTS: cloud is default, sensitive prompts kept on local.
- **Model Router tiers** — heavy reasoning → opus, light/planner → haiku (cost-aware).
- **Prompt caching** — implemented in both Anthropic & OpenAI adapters (cuts input cost).
- **Real tool-use** — Anthropic adapter does full tool-use (unit-tested; live round-trip unverified until a
  key exists — smoke-test it, §6).
- **Cost/observability** — per-run cost + tokens recorded; Costs/Tokens windows populate.

## 6. Switch-day smoke test (5 min)
1. `GET /api/settings` → right provider + `hasAnthropicKey:true`.
2. Ask a trivial question → answer returns, trace shows model = **your real Claude id** (not `mock`/ollama/cache).
3. `see_screen "what's on my screen"` → a real description (vision works).
4. Ask something with a secret → trace shows "Kept on local model for privacy".
5. "build me a small app" → files land under `Projects/…`, no truncation mid-file (confirms §3).
6. Tokens/Costs window shows non-zero spend; confirm the monthly cap is set (§4).

## 7. Baseline quality with the eval harness (do right after switching)
Run **`POST /api/eval/run`** — it sends 5 golden prompts through the brain and grades them (arithmetic,
identity, capabilities, honesty, grounded-refusal). Compare the pass-rate to the pre-switch (Ollama) run.
This is the regression net the switch was built around — a model change can't silently degrade quality if
you baseline it. (Slow on Ollama; fast on Claude.)

## 8. Validate the connectors — one key at a time (they're unproven drafts until you do)
The external connectors (Gumroad, Stripe, Cloudflare, Netlify, Plausible, Ayrshare, Resend, Printful, Etsy,
Shopify) compile and register but **have NOT been tested against their real APIs**. They fail safe (409
"not connected" with no secret), so they're harmless dormant — but don't trust one until proven. Recipe:
1. Store the key: `POST /api/secrets {"name":"<requiredSecret>","value":"..."}` (e.g. `gumroad-token`).
2. **Read action first** (proves auth): e.g. `POST /api/connectors/gumroad/actions/list_products {"args":"{}"}`.
3. **Write action last** (proves payload) — run it through chat so the approval gate fires; paste any API
   error back for an exact fix.
Mark a connector "proven" only after its write path succeeds live.

## Quick reference — files
- Provider/keys/models/caps: `backend/.../ai/JarvisAiProperties.java` + `application.yml` (`jarvis.ai`)
- Per-call provider pick + fallback: `ai/ProviderSwitchingLanguageModel.java`
- Model-tier helpers (isReal / cheap model): `ai/ModelTier.java`
- Spend governor (daily tokens + monthly USD): `ai/TokenBudget.java`
- Runtime switch endpoints: `api/SettingsController.java` (`/provider`, `/budget`)
- Router tiers / model prices: `model/ModelCatalog.java`, `model/ModelRouter.java`
- Vision provider pick: `ai/VisionService.java`
- Eval harness: `brain/EvalService.java` + `POST /api/eval/run`
- Agents (editable prompts + routing): `backend/src/main/resources/agents/*.md` (see `backend/AGENTS.md`)
