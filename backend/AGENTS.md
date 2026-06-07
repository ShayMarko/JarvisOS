# Jarvis Agents

The agent roster lives as editable markdown, one file per agent, in
`src/main/resources/agents/<slug>.md`. Each file is:

```
---
slug: <unique-id>          # routing key (AgentSelector matches on this)
name: "Display Name"
role: "One-line description shown in the UI / roster."
category: <core|dev|files|data|research|writing|revenue|connectors|security|workflows|monitoring|memory>
tools: [tool_a, tool_b, …]        # must be real ToolSpec names registered in the ToolRegistry
keywords: ["cpu", "system status"] # OPTIONAL: routing keywords (quoted → exact match, spaces preserved)
routePriority: 10                  # OPTIONAL: lower = checked first (only matters if keywords are set)
---
<the full system prompt as prose — this is the agent's behaviour>
```

Routing is now self-contained: the AgentSelector builds its ordered keyword table from each agent's
`keywords` + `routePriority` (lower priority checked first; generic catch-alls like `files`=220 go last).
Adding or retuning an agent's routing is a single-file edit — no separate Java rule. Agents with no
`keywords` are reachable only via the LLM router / explicit slug, which is fine for most specialists.

`AgentDefinitionLoader` reads these at boot:
- Bundled defaults come from `classpath:agents/*.md`.
- If the env var `JARVIS_AGENTS_DIR` points at a real folder, any `*.md` there **override by
  slug** — the no-recompile path: drop an edited prompt file there and restart.
- **Fail-fast:** a malformed file (missing `slug`/`name`, empty body, no closing `---`) throws at
  startup with a clear message, so an agent is never silently lost.

To change how an agent behaves, edit its `.md` — no Java, no recompile (restart picks it up).

---

## The shared build contract (HEADLESS_BUILD)

Every code-writing agent's prompt ends with the same build contract. It used to be a shared
`HEADLESS_BUILD` constant in `AgentRegistry.java`; when the prompts moved to markdown it was baked
verbatim into each builder agent's file (general, backend, frontend, codefix, code, author,
productbuilder, appfactory, microapi, seosite, scout, analyst, growth, pod, video). Its rationale,
preserved here so the intent isn't lost:

> The build contract shared by every code-writing agent. Jarvis runs HEADLESS and voice-first
> (often no screen), so code printed into the chat is useless — the deliverable is files on disk.
> This makes `write_file` the ONLY delivery channel and forces a short, speakable summary.

The contract text itself (appended to those agents):

> IMPORTANT — you run HEADLESS and voice-first; there is usually NO screen to read code from.
> NEVER print code, file contents, or a code block in your reply. The ONLY way to deliver code is the
> write_file tool: one call per file, the COMPLETE file content, and the correct nested path under
> Projects/<app-name>/ (e.g. Projects/reminders/backend/src/main/java/App.java,
> Projects/reminders/client/src/App.tsx). Create folders simply by including them in the file path.
> If you are ever about to show code, call write_file with it instead. Build the WHOLE project
> file-by-file (every file needed to run it), then end with a brief, plain-language summary — what you
> built, the folder, the key files, and how to run it — short enough to be read aloud. Do NOT ask
> whether to create files; just build it. Aim for a COMPLETE, well-structured project, not a single
> cram-everything file: split code into separate files by responsibility (entry point, core
> modules/components, config), and include the supporting files a real project has — a dependency/build
> file (requirements.txt, package.json, pom.xml as fits the stack), a README with run steps, and tests
> where it makes sense. Only a genuinely trivial script should be one file.

If you ever want to change the build contract for all builder agents at once, edit the trailing block
in each of those files (or, if you prefer DRY again, reintroduce a shared snippet + a `headless: true`
frontmatter flag the loader appends).
