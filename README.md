# Jarvis AI OS

A local AI operating layer: a unified client, a deterministic command engine, an
AI orchestrator ("Brain"), permanent & temporary agents, controlled file access,
external connectors (MCPs), transparent memory, a RAG knowledge base, system
monitoring, workflows, sandboxed execution, and plugin extensibility.

> **Guiding principle:** *Jarvis owns the architecture; SDKs are adapters.*

Full specification: `Jarvis_AI_OS_Full_Specification_HE.pdf` + architecture diagram (`*.png`) in this folder.

## Status — Phases 1–11 (roadmap complete)

Implements **Phase 1** (spine), **Phase 2** (files + permissions), **Phase 3** (Memory + Flyway), **Phase 4** (real-time monitor), **Phase 5** (Approval + Sandbox + Secrets), **Phase 6** (the Jarvis Brain + agents), **Phase 7** (Connectors / MCPs), **Phase 8** (Workflows + Scheduler + durable runs), **Phase 9** (Knowledge Base / RAG), **Phase 10** (Model Router + Agent Debugger / Observability), and **Phase 11** (Voice + Web search + Plugins). Plus a gap-closure pass: macOS Local Actions, conversation continuity, agentic auto-memory, and a Notification Center.

**Phase 1**
- **Input Router** — classifies input as a slash command vs. a (future) AI request. *Slash command always wins.*
- **Command Engine + Registry** — pluggable `CommandHandler` beans, auto-discovered and indexed by slash.
- **Jarvis Explorer** — a virtual root with standard folders (Downloads, Documents, Projects, …).
- **System Monitor** — CPU / RAM / disk / JVM snapshot.
- **Audit Log** — every action persisted to SQLite.
- **Client** — a React console (command palette, file browser, status cards) over REST.

**Phase 2**
- **PermissionGuard + Permission Modes** (`SAFE` / `ASSISTED` / `AUTONOMOUS`, spec §11.1) — every file op is authorised against the active mode, the folder's permission, and the blocked-paths list; directory traversal is rejected. Deletes require confirmation in `ASSISTED` mode (an Approval Center placeholder → HTTP 422).
- **File operations** — read / write / create / delete / search within the Explorer, exposed over REST and through the internal viewer/editor.
- **Global error handling** — consistent `ApiError` envelope `{ code, message, detail, traceId, timestamp }`; typed `JarvisException` hierarchy; never leaks stack traces.
- **Request trace IDs** — every request gets a trace id (MDC + `X-Trace-Id` header) that appears in logs and error responses.
- **Commands** — `/help`, `/jfiles`, `/searchall`, `/logs`, `/status`, `/settings`.
- **Tests** — backend tests for guard, file system, command engine, controller slice.
- **Client** — internal file viewer/editor (open → edit → save), new file/folder + delete actions, `/logs` viewer, error toasts (with trace id), and an LTR/RTL layout toggle.

**Phase 3**
- **Flyway migrations** — Flyway now owns the schema (`db/migration/V1__init.sql`); Hibernate `ddl-auto: none`. Uses `flyway-core` (bundles SQLite support) + Boot 4's `spring-boot-flyway` autoconfig module.
- **Memory Manager** (spec §10.1, Appendix C) — `Memory` entity with category, title, content, source/provenance, confidence, visibility, sensitivity, created/updated/expires timestamps, enabled. Full CRUD + search + export; every mutation audited. `/memory` command.
- **Client** — Memory Manager screen: cards with provenance/confidence/sensitivity, add/edit modal form (confidence slider, sensitivity/visibility/expiry/enabled controls), delete, and JSON export download.
- **Tests** — 26 backend tests total (added `MemoryService` CRUD/search/expiry).

**Phase 4**
- **Real-time System Monitor** — `SystemMonitorService` enriched (CPU, memory, swap, disk, open file descriptors, threads, committed virtual memory, uptime; agent/task counts are placeholders until Phase 6). An **SSE stream** (`/api/monitor/stream`) pushes a snapshot every 2s; the broadcaster idles when there are no subscribers.
- **Spring Boot Actuator** — `/actuator/health`, `/info`, `/metrics`.
- **Loopback binding** — the server binds `127.0.0.1` by default so other machines can't reach Jarvis (override with `JARVIS_BIND`).
- **Command** — `/resources` opens the live dashboard.
- **Client** — live Resource Dashboard: metric cards + animated CPU/RAM/Swap/Disk gauges + a streaming CPU sparkline, a LIVE indicator, and automatic reconnect (`EventSource`).
- **Tests** — 28 backend tests total.

**Phase 5 — the safety layer**
- **Risk classification** — `RiskClassifier` rates a shell command LOW/MEDIUM/HIGH/CRITICAL by pattern (`rm -rf`, `sudo`, `curl|sh`, `git push`, package installs, …).
- **Approval Center** (spec §11.2) — human-in-the-loop gate. A sensitive action is submitted with its risk + preview + "why"; it's parked as PENDING (the deferred action held in memory) until you **approve** (it runs) or **deny**. Supports "remember this decision" per action type. Persisted audit trail.
- **Sandbox Runtime** (spec §11.4) — runs a command in an isolated, throwaway working directory with a timeout and captured output. *Lightweight* (fresh cwd + timeout + capture), not container-isolated — the strong guarantee is that nothing runs until approved.
- **Terminal capability** — `/terminal <cmd>` classifies risk → Approval Center → runs in the Sandbox on approve.
- **Secrets Vault** (spec §11.3) — AES-GCM encrypted credentials. Only **masked** metadata is ever exposed via the API; plaintext is reachable only by internal connector adapters (`reveal()`), never returned over HTTP or placed in any LLM context. Every access audited.
- **Commands** — `/terminal`, `/approve`, `/sandbox`, `/secrets`, `/stop`.
- **Client** — Approval Center (pending cards, risk badges, approve/deny + remember, sandbox-output view), Secrets Vault (masked list, add modal, revoke).
- **Tests** — 41 backend tests total.

**Phase 6 — the Jarvis Brain + agents**
- **Model adapter** (`LanguageModel`) — the Brain depends only on this interface. **`AnthropicLanguageModel`** is a **real, full tool-use** integration (advertises tools, executes Claude's `tool_use`, feeds `tool_result` back — the same loop the mock drives); activate with `jarvis.ai.provider=claude` + `ANTHROPIC_API_KEY`. Its request-mapping/response-parsing are unit-tested with no network; the live call needs your key. **`MockLanguageModel`** remains as an explicit **offline fallback** so the system still runs with no key.
- **Tools = capability bridge** — `list_files`, `read_file`, `search_files`, `system_status`, `memory_search` wrap existing capabilities so agents *act*, not just talk.
- **Agent runtime** — the tool-calling loop: ask model → run requested tools → feed results back → repeat until a final answer. 7 permanent agents (General, File, Research, System, Knowledge, Data, Code) + temporary-agent factory.
- **Orchestrator (Brain)** — understands the request, builds context from Memory, selects an agent, runs it, records a **Task**, and returns the answer with a transparent **step trace**.
- **Task history** — every request is a persisted `Task` (migration V3); feeds `/tasks` and the live monitor's agent/task counts.
- **Free text now reaches the Brain** via the Input Router; commands `/agents`, `/tasks`.
- **Client** — chat answers with an expandable reasoning trace + agent badge + token count; `/agents` grid; `/tasks` list.
- **Tests** — 52 backend tests total (incl. real Anthropic mapping).

**Phase 7 — Connectors / MCPs**
- **Connector framework** — `Connector` interface + `ConnectorRegistry`; health is derived from the **Secrets Vault** (a credential present → `CONNECTED`, else `DISCONNECTED`). Connectors get their credential only via the vault — never through the LLM.
- **Real API connectors** — GitHub (repos/issues), Slack (channels/post), Gmail (recent messages), Google Calendar (today's events) make **real HTTP calls** (Spring `RestClient`) using the token from the vault. No credential → an honest "not connected" error (no fake data). Gmail/Calendar need a Google OAuth **access token** stored in the vault (the OAuth consent/refresh flow is a separate setup).
- **Agent bridge** — a `connector_invoke` tool lets agents reach external services through the same tool loop; a dedicated **Connector Agent**.
- **Monitor** — `runtime.connectorHealth` reports `connected/total`.
- **Command** — `/connectors`; client Connectors screen (status badges, run actions, "connect → store credential" link to the vault).
- **MCP transport** — deferred: the same `Connector` abstraction will host an `McpConnector` that wraps an MCP server's tools.
- **Tests** — 49 backend tests total.

**Phase 8 — Workflows + Scheduler + Durable Tasks**
- **Workflow engine** — a workflow is a list of steps (`COMMAND`, `BRAIN`, `CONNECTOR`, `NOTIFY`, `APPROVAL`) each calling a **real** capability. The engine runs them sequentially, **persisting state after every step** (durable runs), with **per-step retries**.
- **Approval gates** — an `APPROVAL` step **pauses** the run (status `PAUSED`) and creates an Approval Center request; approving it **resumes** the run from where it left off (ties Phase 8 to Phase 5).
- **Real cron scheduler** — `SCHEDULE` workflows run on a `CronTrigger` (Spring 6-field cron) via a `TaskScheduler`; (re)scheduled on create/update.
- **Triggers** — MANUAL, SCHEDULE, WEBHOOK (the run endpoint doubles as a webhook). FILE_CHANGED / EMAIL_RECEIVED are future.
- **Commands** — `/workflows`, `/scheduler`; client Workflow Builder (create with a step builder, run, delete) + a runs timeline.
- **Tests** — 55 backend tests total (run, retry→fail, approval pause→resume).

**Phase 9 — Knowledge Base / RAG**
- **Pluggable `EmbeddingModel`** — default is an offline `HashingEmbeddingModel` (hashing-trick + sublinear TF + L2-normalise → real cosine ranking, no API key / no model download). A neural embedder drops in behind the interface later.
- **Index → chunk → embed → store** — `KnowledgeBaseService` indexes a file or a folder (or ad-hoc text), chunks (~1000 chars, 150 overlap), embeds each chunk, and persists vectors in SQLite (migration V6). Re-indexing a source replaces it.
- **Semantic search with citations** — cosine ranking over chunks returns the source document + snippet + score.
- **Ask-your-documents is agentic** — a `kb_search` tool wired into the Research/Knowledge/General agents, so the Brain retrieves and answers with sources (no hard-coded RAG pipeline).
- **Command** — `/kb` (and `/kb <query>` to search); client Knowledge Base screen (index, search with scores, document list).
- **Tests** — 58 backend tests total (embedding determinism/ranking, index+retrieve relevance).

**Phase 10 — Model Router + Agent Debugger / Observability**
- **Model Router** — a `ModelCatalog` (local mock + Claude tiers with cost/quality metadata; the active adapter is flagged available) and a policy-based `ModelRouter` (`BALANCED` / `PRIVATE` / `QUALITY` / `CHEAP`) that picks among *available* models, plus `CostCalculator`. The Brain routes a model per request and records it.
- **Observability** — every Brain run is persisted as an `AgentRunRecord` (migration V7): agent, model, request/answer, status, prompt/completion tokens, **cost**, **duration**, and the full **step trace** (JSON).
- **Agent Debugger** — `/debugger` lists runs; each expands to the trace; **`/replay <id>`** re-issues the original request.
- **Cost / Token Monitor** — `/costs` aggregates tokens + cost, by model and by agent.
- **Command** — `/models`, `/debugger`, `/replay`, `/costs`; client screens for each.
- **Tests** — 64 backend tests total (router scoring, cost math).

**Phase 11 — Voice + Web + Plugins (offline-first; no keys)**
- **Voice** — browser Web Speech API: 🔊 **speak answers** (TTS), 🎤 **dictation** (STT), and a 👂 **"Jarvis" wake-word** toggle (continuous listen → submit what follows). No key, runs on OS voices; plus a macOS `say` tool/`/say` command.
- **Web search** — `WebSearchService` via **DuckDuckGo Instant Answer (keyless)** + a `web_search` agent tool + `/web`; and a real `fetch_url` tool (HTTP GET → visible text) for static pages.
- **macOS media** — `image_convert` tool via `sips` (closes the convert/resize gap).
- **Plugin / Extension surface** — `PluginRegistry` introspects the four live extension points (commands/tools/connectors/agents); `/plugins` + `/api/plugins`. Adding a Spring bean at any point extends Jarvis.
- **Tests** — 67 backend tests total (web-search parse).

### Deferred to follow-up (need external pieces — documented honestly)
- **Full browser automation** (JS-rendered pages, form-filling) → a **Playwright Node sidecar** (`fetch_url` covers static pages now).
- **Media *generation*** (text→image/video/audio) and **PDF/Word creation** → need a provider/library.
- **Live Anthropic** reasoning → set `jarvis.ai.provider=claude` + `ANTHROPIC_API_KEY` (full tool-use is built + unit-tested; live call unverified).
- **Neural embeddings** for RAG (offline lexical-TF today) → drop in behind `EmbeddingModel`, likely with Postgres + pgvector.
- **Desktop packaging** (Tauri/Electron native window) and a **plugin marketplace / external-jar loader**.
- **Crash auto-resume** of mid-flight workflow runs; **Privacy-Agent masking** before external model calls.

## Tech stack

| Layer    | Choice                                              |
| -------- | --------------------------------------------------- |
| Backend  | Java 17 + Spring Boot 4 (Maven, via `mvnw` wrapper) |
| Database | SQLite (local-first) via Hibernate community dialect |
| Client   | React 19 + TypeScript + Vite                        |

## Project layout

```
backend/   Spring Boot core (com.jarvis.*)
  ├─ command/    Command Engine, registry, handlers
  ├─ input/      Input Router
  ├─ explorer/   Jarvis Explorer file system capability
  ├─ system/     System monitor
  ├─ audit/      Audit log (JPA + SQLite)
  ├─ config/     Filesystem properties, CORS
  └─ api/        REST controllers
client/    React + Vite client
data/      SQLite database file (created at runtime, git-ignored)
JarvisExplorer/  Virtual Jarvis home (created at runtime, git-ignored)
```

## Running it

**1. Backend** (port `8088`):

```bash
cd backend
./mvnw spring-boot:run
```

**2. Client** (port `5173`, proxies `/api` → `:8088`):

```bash
cd client
npm install   # first time only
npm run dev
```

Open http://localhost:5173 and try `/help`, `/jfiles`, `/status`, `/settings`.

### Configuration

Edit `backend/src/main/resources/application.yml` (`jarvis.filesystem`) to set the
Jarvis Explorer root, allowed external folders, and blocked paths. Overridable via
env vars: `JARVIS_PORT`, `JARVIS_DB_PATH`, `JARVIS_ROOT`.

## API

| Method | Endpoint                          | Purpose                               |
| ------ | --------------------------------- | ------------------------------------- |
| POST   | `/api/command`                    | Execute any input → structured result |
| GET    | `/api/commands`                   | Command catalogue (for /help)         |
| GET    | `/api/files?path=`                | List a Jarvis Explorer folder         |
| GET    | `/api/files/content?path=`        | Read a text file                      |
| PUT    | `/api/files/content`              | Write a text file `{path, content}`   |
| POST   | `/api/files/dir`                  | Create a folder `{path}`              |
| DELETE | `/api/files?path=&confirm=`       | Delete a file/empty folder            |
| GET    | `/api/search?q=&path=`            | Search files by name                  |
| GET    | `/api/memory?q=`                  | List/search memories                  |
| POST   | `/api/memory`                     | Create a memory                       |
| PUT    | `/api/memory/{id}`                | Update a memory                       |
| DELETE | `/api/memory/{id}`                | Delete a memory                       |
| GET    | `/api/memory/export`              | Export all memories (JSON)            |
| GET    | `/api/status`                     | System monitor snapshot               |
| GET    | `/api/monitor/stream`             | Live metric snapshots (SSE)           |
| POST   | `/api/chat`                       | Ask the Brain `{message}` → answer + trace |
| GET    | `/api/agents`                     | Registered agents                     |
| GET    | `/api/tasks?limit=`               | Recent task history                   |
| GET    | `/api/connectors`                 | Connectors + health                   |
| POST   | `/api/connectors/{id}/actions/{a}`| Invoke a connector action             |
| GET    | `/api/workflows`                  | List workflows                        |
| POST   | `/api/workflows`                  | Create a workflow                     |
| POST   | `/api/workflows/{id}/run`         | Run (manual/webhook) → durable run    |
| GET    | `/api/workflows/runs`             | Recent runs (timeline)                |
| GET    | `/api/approvals`                  | Pending approval requests             |
| POST   | `/api/approvals/{id}/approve`     | Approve → runs the deferred action    |
| POST   | `/api/approvals/{id}/deny`        | Deny a pending request                |
| GET    | `/api/secrets`                    | List secrets (masked, never plaintext)|
| POST   | `/api/secrets`                    | Store an encrypted secret             |
| DELETE | `/api/secrets/{id}`               | Revoke a secret                       |
| GET    | `/api/audit?limit=`               | Recent audit log entries              |
| GET    | `/actuator/health`                | Health check (Actuator)               |

Errors return the `ApiError` envelope and an `X-Trace-Id` header.

## Roadmap (next)

Per the spec: Phase 2 — File access + Permissions; Phase 3 — Memory Manager;
Phase 4 — System Monitor UI; Phase 5 — Sandbox + Approval Center; Phase 6 — Agent
system + Jarvis Brain; then Connectors/MCPs, Workflows, Knowledge Base/RAG, Model
Router + Observability, and Media/automation.
