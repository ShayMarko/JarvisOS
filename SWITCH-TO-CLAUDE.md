# Switching Jarvis → Claude / OpenAI — one-page checklist

The framework is provider-agnostic and switch-ready: `ProviderSwitchingLanguageModel` picks the backing
provider **per call** from `jarvis.ai.provider`, rebuilds the adapter when a key appears, falls back to the
offline mock only on transient/network errors, and surfaces real auth errors instead of hiding them. No
rewiring needed — but do the steps below so it "just works."

## 1. Set the keys + provider
- Keys are read from **env vars at boot** (bound in `application.yml`), and are NOT settable from the UI:
  - `export ANTHROPIC_API_KEY=sk-ant-...`   (and/or `export OPENAI_API_KEY=sk-...`)
- Pick the active provider (either is fine):
  - boot env: `export JARVIS_AI_PROVIDER=claude`  (or `openai`), **then restart the backend**, **or**
  - runtime, no restart: `POST /api/settings/provider {"provider":"claude"}` (key must already be in env).
- Verify: `GET /api/settings` → `hasAnthropicKey:true`, `provider:"claude"`.
- Keep **Ollama running** even after switching: the Privacy Router swaps sensitive prompts to the LOCAL
  model, and embeddings/KB still use it. Don't shut it down.

## 2. ⚠️ Landmine A — set real model IDs (now ALL in yaml; ModelCatalog no longer hardcodes them)
The Anthropic tiers default to placeholder names (`claude-opus-4-8` etc.). They're now **fully yaml-controlled**
(one place) — update these in `application.yml` under `jarvis.ai` to real IDs valid on your account on switch day:
- `model` = HEAVY tier / main, `claude-standard-model` = STANDARD, `planner-model-claude` = LIGHT/planner
- `openai-model`, `planner-model-openai`
Pick a vision-capable model for the main `model` (Claude models are; for OpenAI prefer `gpt-4o` over `-mini`)
since `VisionService` uses `ai.getModel()` / `ai.getOpenaiModel()`. (No code edit needed — `ModelCatalog` reads
these props live.)

## 3. ⚠️ Landmine B — `max-tokens: 1024` will truncate big outputs
This caps **paid-provider completion at 1024 tokens** — the same class of bug that truncated local builds
(fixed there via `ollama-num-predict: 8192`). Zero-to-hero builds writing large files on Claude will get cut
off. Raise `jarvis.ai.max-tokens` (e.g. 4096–8192) before doing real builds.

## 4. ⚠️ Landmine C — `daily-token-budget: 0` = unlimited spend
Console billing is pay-per-token (unlike the flat CLI sub). Set a real cap:
`export JARVIS_AI_DAILY_TOKEN_BUDGET=2000000` (the governor + kill-switch + HUD meter already enforce it).

## 5. What lights up automatically on the switch (no action)
- **Vision** — `describe_image` / `see_screen` start returning real descriptions (were "needs a key").
- **Privacy Router** — now actually ACTS: cloud is the default, sensitive prompts get kept on local.
- **Model Router tiers** — heavy reasoning → opus, light/planner → haiku (cost-aware).
- **Prompt caching** — already implemented in both the Anthropic & OpenAI adapters (cuts input cost).
- **Real tool-use** — Anthropic adapter does full tool-use (unit-tested; the live round-trip is unverified
  until a key exists — smoke-test it, see §6).
- **Cost/observability** — per-run cost + tokens recorded; the Costs/Tokens windows populate.

## 6. Switch-day smoke test (5 min)
1. `GET /api/settings` shows the right provider + `hasAnthropicKey:true`.
2. Ask a trivial question → answer returns, agent-run trace shows model = your Claude id (not `mock`/cache).
3. `see_screen "what's on my screen"` → a real description (vision works).
4. Ask something with a secret (e.g. an API key) → trace shows "Kept on local model for privacy".
5. "build me a small app" → files land under `Projects/…`, no truncation mid-file (confirms §3).
6. Check the Tokens/Costs window shows non-zero spend; confirm the daily budget cap is set (§4).

## Quick reference — files
- Provider/keys/models/caps: `backend/.../ai/JarvisAiProperties.java` + `application.yml` (`jarvis.ai`)
- Per-call provider pick + fallback: `ai/ProviderSwitchingLanguageModel.java`
- Runtime switch endpoint: `api/SettingsController.java` (`POST /api/settings/provider`, `/budget`)
- Router tiers / model IDs: `model/ModelCatalog.java`, `model/ModelRouter.java`
- Vision provider pick: `ai/VisionService.java`
