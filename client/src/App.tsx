import { useEffect, useRef, useState } from 'react'
import {
  ApiError,
  approveRequest,
  createDir,
  createMemory,
  createSecret,
  createWorkflow,
  deleteFile,
  deleteKbDocument,
  deleteMemory,
  deleteSecret,
  deleteWorkflow,
  denyRequest,
  exportMemory,
  fetchCommands,
  getFileContent,
  getNotifications,
  getUnreadCount,
  indexKb,
  invokeConnector,
  markNotificationsRead,
  replayRun,
  revealFile,
  runCommand,
  runWorkflow,
  updateMemory,
  writeFile,
  type AgentDef,
  type ApprovalRequest,
  type AuditEntry,
  type ChatResponse,
  type CommandDefinition,
  type CommandResult,
  type ConnectorInfo,
  type CostsData,
  type FileNode,
  type KbData,
  type KbHit,
  type Memory,
  type MemoryDraft,
  type ModelsData,
  type NotificationItem,
  type PluginsData,
  type RiskLevel,
  type RunRecord,
  type RunView,
  type SandboxResult,
  type SecretDraft,
  type SecretView,
  type Step,
  type TaskItem,
  type WfStep,
  type WfStepType,
  type WorkflowDraft,
  type WorkflowsData,
  type WorkflowView,
} from './api'

interface Entry {
  id: number
  input: string
  result?: CommandResult
  error?: string
  pending: boolean
}

interface Toast {
  kind: 'ok' | 'error'
  text: string
  traceId?: string
}

interface EditorState {
  path: string
  content: string
  original: string
  saving: boolean
}

/** File operations shared with the FilesView renderer. */
export interface FileActions {
  open: (path: string) => void
  remove: (path: string, parent: string) => void
  newFile: (base: string) => void
  newFolder: (base: string) => void
}

/** Memory operations shared with the MemoryView renderer. */
export interface MemoryActions {
  add: () => void
  edit: (m: Memory) => void
  remove: (m: Memory) => void
  exportAll: () => void
}

/** Approval operations shared with the ApprovalView renderer. */
export interface ApprovalActions {
  approve: (id: string, remember: boolean) => void
  deny: (id: string, remember: boolean) => void
}

/** Secret operations shared with the SecretsView renderer. */
export interface SecretActions {
  add: () => void
  remove: (s: SecretView) => void
}

/** Workflow operations shared with the WorkflowsView renderer. */
export interface WorkflowActions {
  add: () => void
  run: (id: string) => void
  remove: (w: WorkflowView) => void
}

interface WfEditorState {
  draft: WorkflowDraft
  saving: boolean
}

const blankWorkflow: WorkflowDraft = {
  name: '',
  description: '',
  triggerType: 'MANUAL',
  cron: '',
  enabled: true,
  steps: [],
}

interface SecretEditorState {
  draft: SecretDraft
  saving: boolean
}

interface MemEditorState {
  id: string | null // null = creating
  draft: MemoryDraft
  saving: boolean
}

const blankMemory: MemoryDraft = {
  category: 'preference',
  title: '',
  content: '',
  source: 'manual',
  confidence: 1,
  sensitivity: 'NORMAL',
  visibility: 'USER_VISIBLE',
  enabled: true,
}

let counter = 0

const join = (base: string, name: string) => (base ? `${base}/${name}` : name)

/** Browser-native TTS (Web Speech API) — no key, runs on the OS voices. */
function speak(text: string) {
  try {
    const synth = window.speechSynthesis
    if (!synth || !text) return
    synth.cancel()
    const u = new SpeechSynthesisUtterance(text.slice(0, 600))
    u.rate = 1.05
    synth.speak(u)
  } catch {
    /* TTS unsupported */
  }
}

/** Text Jarvis should speak for a given result (answers + plain messages). */
function spokenText(result: CommandResult): string {
  if (result.type === 'chat') return (result.data as ChatResponse).answer
  if (result.type === 'message' || result.type === 'sandbox') return result.message
  return ''
}

/** Web Speech recognition factory (Chrome/Safari), or null if unsupported. */
function newRecognition(): any {
  const w = window as any
  const Ctor = w.SpeechRecognition || w.webkitSpeechRecognition
  if (!Ctor) return null
  const r = new Ctor()
  r.lang = 'en-US'
  r.interimResults = false
  return r
}

export default function App() {
  const [commands, setCommands] = useState<CommandDefinition[]>([])
  const [entries, setEntries] = useState<Entry[]>([])
  const [input, setInput] = useState('')
  const [online, setOnline] = useState<boolean | null>(null)
  const [toast, setToast] = useState<Toast | null>(null)
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [memEditor, setMemEditor] = useState<MemEditorState | null>(null)
  const [secretEditor, setSecretEditor] = useState<SecretEditorState | null>(null)
  const [wfEditor, setWfEditor] = useState<WfEditorState | null>(null)
  const [rtl, setRtl] = useState(false)
  const [ttsOn, setTtsOn] = useState(false)
  const [listening, setListening] = useState(false)
  const [wakeOn, setWakeOn] = useState(false)
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    fetchCommands()
      .then((c) => {
        setCommands(c)
        setOnline(true)
      })
      .catch(() => setOnline(false))
  }, [])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }, [entries])

  useEffect(() => {
    document.documentElement.dir = rtl ? 'rtl' : 'ltr'
  }, [rtl])

  function notify(kind: Toast['kind'], text: string, traceId?: string) {
    setToast({ kind, text, traceId })
    window.setTimeout(() => setToast(null), 5000)
  }

  function reportError(err: unknown, fallback: string) {
    if (err instanceof ApiError) {
      notify('error', err.message, err.traceId)
    } else {
      notify('error', `${fallback}: ${String(err)}`)
    }
  }

  async function submit(raw: string) {
    const value = raw.trim()
    if (!value) return
    const id = ++counter
    setEntries((e) => [...e, { id, input: value, pending: true }])
    setInput('')
    try {
      const result = await runCommand(value)
      setOnline(true)
      setEntries((e) => e.map((x) => (x.id === id ? { ...x, result, pending: false } : x)))
      if (ttsOn) {
        const toSpeak = spokenText(result)
        if (toSpeak) speak(toSpeak)
      }
    } catch (err) {
      setOnline(false)
      setEntries((e) =>
        e.map((x) => (x.id === id ? { ...x, error: String(err), pending: false } : x)),
      )
    }
  }

  function dictate() {
    const r = newRecognition()
    if (!r) {
      notify('error', 'Voice input not supported in this browser')
      return
    }
    r.onstart = () => setListening(true)
    r.onend = () => setListening(false)
    r.onerror = () => setListening(false)
    r.onresult = (e: any) => {
      const transcript = e.results[0][0].transcript as string
      if (transcript) submit(transcript)
    }
    r.start()
  }

  // Wake-word: continuous listening that submits whatever follows "Jarvis".
  useEffect(() => {
    if (!wakeOn) return
    const r = newRecognition()
    if (!r) {
      notify('error', 'Wake word not supported in this browser')
      setWakeOn(false)
      return
    }
    r.continuous = true
    r.onresult = (e: any) => {
      const t = (e.results[e.results.length - 1][0].transcript as string).toLowerCase()
      const idx = t.indexOf('jarvis')
      if (idx >= 0) {
        const cmd = t.slice(idx + 'jarvis'.length).trim()
        if (cmd) submit(cmd)
      }
    }
    r.onend = () => {
      // keep listening while the toggle is on
      try {
        r.start()
      } catch {
        /* already started */
      }
    }
    try {
      r.start()
    } catch {
      /* ignore */
    }
    return () => {
      r.onend = null
      try {
        r.stop()
      } catch {
        /* ignore */
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wakeOn])

  const fileActions: FileActions = {
    open: async (path) => {
      try {
        const fc = await getFileContent(path)
        setEditor({ path: fc.path, content: fc.content, original: fc.content, saving: false })
      } catch (err) {
        reportError(err, 'Could not open file')
      }
    },
    remove: async (path, parent) => {
      if (!window.confirm(`Delete "${path}"? This cannot be undone.`)) return
      try {
        await deleteFile(path, true)
        notify('ok', `Deleted ${path}`)
        submit(`/jfiles ${parent}`)
      } catch (err) {
        reportError(err, 'Could not delete')
      }
    },
    newFile: async (base) => {
      const name = window.prompt('New file name:')
      if (!name) return
      try {
        await writeFile(join(base, name), '')
        notify('ok', `Created ${name}`)
        submit(`/jfiles ${base}`)
      } catch (err) {
        reportError(err, 'Could not create file')
      }
    },
    newFolder: async (base) => {
      const name = window.prompt('New folder name:')
      if (!name) return
      try {
        await createDir(join(base, name))
        notify('ok', `Created ${name}/`)
        submit(`/jfiles ${base}`)
      } catch (err) {
        reportError(err, 'Could not create folder')
      }
    },
  }

  async function saveEditor() {
    if (!editor) return
    setEditor({ ...editor, saving: true })
    try {
      await writeFile(editor.path, editor.content)
      notify('ok', `Saved ${editor.path}`)
      setEditor((e) => (e ? { ...e, original: e.content, saving: false } : e))
    } catch (err) {
      reportError(err, 'Could not save')
      setEditor((e) => (e ? { ...e, saving: false } : e))
    }
  }

  const memoryActions: MemoryActions = {
    add: () => setMemEditor({ id: null, draft: { ...blankMemory }, saving: false }),
    edit: (m) =>
      setMemEditor({
        id: m.id,
        draft: {
          category: m.category,
          title: m.title,
          content: m.content,
          source: m.source,
          confidence: m.confidence,
          visibility: m.visibility,
          sensitivity: m.sensitivity,
          expiresAt: m.expiresAt,
          enabled: m.enabled,
        },
        saving: false,
      }),
    remove: async (m) => {
      if (!window.confirm(`Delete memory "${m.title}"?`)) return
      try {
        await deleteMemory(m.id)
        notify('ok', `Deleted "${m.title}"`)
        submit('/memory')
      } catch (err) {
        reportError(err, 'Could not delete memory')
      }
    },
    exportAll: async () => {
      try {
        const all = await exportMemory()
        const blob = new Blob([JSON.stringify(all, null, 2)], { type: 'application/json' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = 'jarvis-memory.json'
        a.click()
        URL.revokeObjectURL(url)
        notify('ok', `Exported ${all.length} memories`)
      } catch (err) {
        reportError(err, 'Could not export')
      }
    },
  }

  async function saveMemEditor() {
    if (!memEditor) return
    setMemEditor({ ...memEditor, saving: true })
    try {
      if (memEditor.id) {
        await updateMemory(memEditor.id, memEditor.draft)
        notify('ok', 'Memory updated')
      } else {
        await createMemory(memEditor.draft)
        notify('ok', 'Memory added')
      }
      setMemEditor(null)
      submit('/memory')
    } catch (err) {
      reportError(err, 'Could not save memory')
      setMemEditor((e) => (e ? { ...e, saving: false } : e))
    }
  }

  function pushResult(input: string, result: CommandResult) {
    const id = ++counter
    setEntries((e) => [...e, { id, input, result, pending: false }])
  }

  const approvalActions: ApprovalActions = {
    approve: async (id, remember) => {
      try {
        const decision = await approveRequest(id, remember)
        notify('ok', `Approved ${id}`)
        if (decision.result) {
          pushResult(`▶ ${decision.request.title}`, {
            status: 'OK',
            type: 'sandbox',
            message: 'Sandbox output',
            data: decision.result,
          })
        }
        submit('/approve')
      } catch (err) {
        reportError(err, 'Could not approve')
      }
    },
    deny: async (id, remember) => {
      try {
        await denyRequest(id, remember)
        notify('ok', `Denied ${id}`)
        submit('/approve')
      } catch (err) {
        reportError(err, 'Could not deny')
      }
    },
  }

  const secretActions: SecretActions = {
    add: () => setSecretEditor({ draft: { name: '', connector: '', value: '', scopes: [] }, saving: false }),
    remove: async (s) => {
      if (!window.confirm(`Revoke secret "${s.name}"? This deletes it permanently.`)) return
      try {
        await deleteSecret(s.id)
        notify('ok', `Revoked ${s.name}`)
        submit('/secrets')
      } catch (err) {
        reportError(err, 'Could not revoke')
      }
    },
  }

  async function saveSecretEditor() {
    if (!secretEditor) return
    setSecretEditor({ ...secretEditor, saving: true })
    try {
      await createSecret(secretEditor.draft)
      notify('ok', 'Secret stored (encrypted)')
      setSecretEditor(null)
      submit('/secrets')
    } catch (err) {
      reportError(err, 'Could not store secret')
      setSecretEditor((e) => (e ? { ...e, saving: false } : e))
    }
  }

  const workflowActions: WorkflowActions = {
    add: () => setWfEditor({ draft: { ...blankWorkflow, steps: [] }, saving: false }),
    run: async (id) => {
      try {
        const run = await runWorkflow(id)
        notify(run.status === 'FAILED' ? 'error' : 'ok', `Workflow ${run.status.toLowerCase()}`)
        submit('/workflows')
      } catch (err) {
        reportError(err, 'Could not run workflow')
      }
    },
    remove: async (w) => {
      if (!window.confirm(`Delete workflow "${w.name}"?`)) return
      try {
        await deleteWorkflow(w.id)
        notify('ok', `Deleted ${w.name}`)
        submit('/workflows')
      } catch (err) {
        reportError(err, 'Could not delete workflow')
      }
    },
  }

  async function doReplay(id: string) {
    try {
      const r = (await replayRun(id)) as ChatResponse
      notify('ok', `Replayed ${id}`)
      pushResult(`▶ replay ${id}`, { status: 'OK', type: 'chat', message: r.answer, data: r })
      submit('/debugger')
    } catch (err) {
      reportError(err, 'Replay failed')
    }
  }

  const kbActions = {
    index: async () => {
      const path = window.prompt('Index which Explorer path? (a file or a folder, e.g. Notes)')
      if (path === null) return
      try {
        await indexKb({ path })
        notify('ok', `Indexed ${path || 'root'}`)
        submit('/kb')
      } catch (err) {
        reportError(err, 'Could not index')
      }
    },
    remove: async (id: string) => {
      if (!window.confirm('Remove this document from the Knowledge Base?')) return
      try {
        await deleteKbDocument(id)
        notify('ok', 'Removed from Knowledge Base')
        submit('/kb')
      } catch (err) {
        reportError(err, 'Could not remove')
      }
    },
  }

  async function saveWfEditor() {
    if (!wfEditor) return
    setWfEditor({ ...wfEditor, saving: true })
    try {
      await createWorkflow(wfEditor.draft)
      notify('ok', 'Workflow created')
      setWfEditor(null)
      submit('/workflows')
    } catch (err) {
      reportError(err, 'Could not create workflow')
      setWfEditor((e) => (e ? { ...e, saving: false } : e))
    }
  }

  const grouped = groupByCategory(commands)

  return (
    <div className="jarvis">
      <header className="topbar">
        <div className="brand">
          <span className="logo">◉</span> JARVIS <span className="brand-sub">AI OS</span>
        </div>
        <div className="topbar-right">
          <NotificationBell online={online !== false} />
          <button
            className={`dir-toggle ${ttsOn ? 'toggle-on' : ''}`}
            onClick={() => setTtsOn((v) => !v)}
            title="Speak answers (text-to-speech)"
          >
            {ttsOn ? '🔊' : '🔈'}
          </button>
          <button
            className={`dir-toggle ${wakeOn ? 'toggle-on' : ''}`}
            onClick={() => setWakeOn((v) => !v)}
            title='Wake word — say "Jarvis …"'
          >
            👂{wakeOn ? ' on' : ''}
          </button>
          <button className="dir-toggle" onClick={() => setRtl((v) => !v)} title="Toggle layout direction">
            {rtl ? 'RTL' : 'LTR'}
          </button>
          <span className="badge">Phase 11</span>
          <span className={`status-dot ${online === false ? 'off' : online ? 'on' : ''}`} />
          <span className="status-text">
            {online === null ? 'connecting…' : online ? 'core online' : 'core offline'}
          </span>
        </div>
      </header>

      <div className="layout">
        <aside className="sidebar">
          <div className="sidebar-title">Commands</div>
          {online === false && <div className="hint">Start the backend on :8088</div>}
          {Object.entries(grouped).map(([category, cmds]) => (
            <div key={category} className="cmd-group">
              <div className="cmd-group-title">{category}</div>
              {cmds.map((c) => (
                <button key={c.slash} className="cmd-item" title={c.description} onClick={() => submit(c.slash)}>
                  <span className="cmd-slash">{c.slash}</span>
                  <span className="cmd-desc">{c.description}</span>
                </button>
              ))}
            </div>
          ))}
        </aside>

        <main className="console" ref={scrollRef}>
          {entries.length === 0 && <Welcome onPick={submit} />}
          {entries.map((entry) => (
            <div key={entry.id} className="entry">
              <div className="prompt-line">
                <span className="caret">›</span> {entry.input}
              </div>
              {entry.pending && <div className="thinking">working…</div>}
              {entry.error && <div className="result error">⚠ {entry.error}</div>}
              {entry.result && (
                <ResultView
                  result={entry.result}
                  onNavigate={submit}
                  fileActions={fileActions}
                  memoryActions={memoryActions}
                  approvalActions={approvalActions}
                  secretActions={secretActions}
                  workflowActions={workflowActions}
                  kbActions={kbActions}
                  onReplay={doReplay}
                />
              )}
            </div>
          ))}
        </main>
      </div>

      <form
        className="inputbar"
        onSubmit={(e) => {
          e.preventDefault()
          submit(input)
        }}
      >
        <span className="input-caret">›</span>
        <input
          autoFocus
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Type a command (e.g. /help) or ask Jarvis…"
          spellCheck={false}
        />
        <button
          type="button"
          className={`mic ${listening ? 'listening' : ''}`}
          onClick={dictate}
          title="Dictate (speech-to-text)"
        >
          🎤
        </button>
        <button type="submit">Send</button>
      </form>

      {editor && (
        <Editor
          editor={editor}
          onChange={(content) => setEditor({ ...editor, content })}
          onSave={saveEditor}
          onClose={() => setEditor(null)}
          onReveal={async () => {
            try {
              await revealFile(editor.path)
              notify('ok', 'Opened location in Finder')
            } catch (err) {
              reportError(err, 'Could not open location')
            }
          }}
        />
      )}

      {memEditor && (
        <MemoryEditor
          state={memEditor}
          onChange={(draft) => setMemEditor({ ...memEditor, draft })}
          onSave={saveMemEditor}
          onClose={() => setMemEditor(null)}
        />
      )}

      {secretEditor && (
        <SecretEditor
          state={secretEditor}
          onChange={(draft) => setSecretEditor({ ...secretEditor, draft })}
          onSave={saveSecretEditor}
          onClose={() => setSecretEditor(null)}
        />
      )}

      {wfEditor && (
        <WorkflowEditor
          state={wfEditor}
          onChange={(draft) => setWfEditor({ ...wfEditor, draft })}
          onSave={saveWfEditor}
          onClose={() => setWfEditor(null)}
        />
      )}

      {toast && (
        <div className={`toast ${toast.kind}`} onClick={() => setToast(null)}>
          <div>{toast.text}</div>
          {toast.traceId && toast.traceId !== '-' && <div className="toast-trace">trace {toast.traceId}</div>}
        </div>
      )}
    </div>
  )
}

function Welcome({ onPick }: { onPick: (s: string) => void }) {
  return (
    <div className="welcome">
      <h1>Welcome to Jarvis</h1>
      <p>Your local AI operating layer. Phase 11: voice, web search & plugins — speak or type, or use a /command.</p>
      <div className="welcome-cmds">
        {['/help', '/plugins', '/web', '/kb', '/models', '/workflows'].map((c) => (
          <button key={c} onClick={() => onPick(c)}>
            {c}
          </button>
        ))}
      </div>
    </div>
  )
}

function ResultView({
  result,
  onNavigate,
  fileActions,
  memoryActions,
  approvalActions,
  secretActions,
  workflowActions,
  kbActions,
  onReplay,
}: {
  result: CommandResult
  onNavigate: (s: string) => void
  fileActions: FileActions
  memoryActions: MemoryActions
  approvalActions: ApprovalActions
  secretActions: SecretActions
  workflowActions: WorkflowActions
  kbActions: { index: () => void; remove: (id: string) => void }
  onReplay: (id: string) => void
}) {
  if (result.status === 'ERROR') {
    return <div className="result error">⚠ {result.message}</div>
  }
  switch (result.type) {
    case 'help':
      return <HelpView commands={result.data as CommandDefinition[]} onPick={onNavigate} />
    case 'files':
      return (
        <FilesView
          data={result.data as { path: string; entries: FileNode[] }}
          onNavigate={onNavigate}
          actions={fileActions}
        />
      )
    case 'chat':
      return <ChatView chat={result.data as ChatResponse} />
    case 'agents':
      return <AgentsView agents={result.data as AgentDef[]} />
    case 'tasks':
      return <TasksView tasks={result.data as TaskItem[]} />
    case 'models':
      return <ModelsView data={result.data as ModelsData} />
    case 'runs':
      return <DebuggerView runs={result.data as RunRecord[]} onReplay={onReplay} />
    case 'costs':
      return <CostsView data={result.data as CostsData} />
    case 'plugins':
      return <PluginsView data={result.data as PluginsData} onPick={onNavigate} />
    case 'workflows':
      return <WorkflowsView data={result.data as WorkflowsData} actions={workflowActions} />
    case 'connectors':
      return <ConnectorsView connectors={result.data as ConnectorInfo[]} onNavigate={onNavigate} />
    case 'memory':
      return <MemoryView memories={result.data as Memory[]} actions={memoryActions} />
    case 'kb':
      return (
        <KbView
          data={result.data as KbData}
          onSearch={(q) => onNavigate('/kb ' + q)}
          onIndex={kbActions.index}
          onRemove={kbActions.remove}
        />
      )
    case 'status':
      return <StatusView data={result.data as Record<string, unknown>} />
    case 'resources':
      return <ResourcesView initial={result.data as Record<string, unknown>} />
    case 'approvals':
      return <ApprovalView requests={result.data as ApprovalRequest[]} actions={approvalActions} />
    case 'approval-pending':
      return (
        <ApprovalPending request={result.data as ApprovalRequest} message={result.message} onOpen={() => onNavigate('/approve')} />
      )
    case 'sandbox':
      return <SandboxView result={result.data as SandboxResult} />
    case 'secrets':
      return <SecretsView secrets={result.data as SecretView[]} actions={secretActions} />
    case 'logs':
      return <LogsView entries={result.data as AuditEntry[]} />
    case 'settings':
      return <JsonView label={result.message} data={result.data} />
    default:
      return <div className="result message">{result.message}</div>
  }
}

const STEP_ICON: Record<string, string> = {
  intent: '🧠',
  agent: '🤖',
  tool: '🔧',
  answer: '✍️',
}

function ChatView({ chat }: { chat: ChatResponse }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="result chat">
      <div className="chat-answer">{chat.answer}</div>
      <div className="chat-meta">
        <span className="chip accent-chip">🤖 {chat.agent}</span>
        <span className="muted small">{chat.model}</span>
        <span className="muted small">{chat.tokens} tokens</span>
        <button className="trace-toggle" onClick={() => setOpen((v) => !v)}>
          {open ? 'hide' : 'show'} reasoning ({chat.steps.length})
        </button>
      </div>
      {open && (
        <ol className="trace">
          {chat.steps.map((s: Step, i) => (
            <li key={i}>
              <span className="trace-icon">{STEP_ICON[s.kind] ?? '•'}</span>
              <span className="trace-label">{s.label}</span>
              {s.detail && <span className="trace-detail muted small">{s.detail}</span>}
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

function AgentsView({ agents }: { agents: AgentDef[] }) {
  return (
    <div className="result">
      <div className="muted small">{agents.length} agents</div>
      <div className="agent-grid">
        {agents.map((a) => (
          <div key={a.slug} className="agent-card">
            <div className="agent-name">🤖 {a.name}</div>
            <div className="muted small agent-role">{a.role}</div>
            <div className="mem-badges">
              {a.toolNames.map((t) => (
                <span key={t} className="chip">
                  {t}
                </span>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function TasksView({ tasks }: { tasks: TaskItem[] }) {
  return (
    <div className="result">
      <div className="muted small">{tasks.length} tasks</div>
      {tasks.length === 0 && <div className="muted small">No tasks yet — ask Jarvis something.</div>}
      <table className="data-table">
        <tbody>
          {tasks.map((t) => (
            <tr key={t.id}>
              <td className={`small ${t.status === 'FAILED' ? 'danger' : 'accent'}`}>{t.status}</td>
              <td className="small">{t.agent ?? '—'}</td>
              <td className="small">{t.request}</td>
              <td className="muted small mono">{new Date(t.createdAt).toLocaleTimeString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ConnectorsView({
  connectors,
  onNavigate,
}: {
  connectors: ConnectorInfo[]
  onNavigate: (s: string) => void
}) {
  return (
    <div className="result">
      <div className="muted small">{connectors.length} connectors</div>
      <div className="agent-grid">
        {connectors.map((c) => (
          <ConnectorCard key={c.id} connector={c} onNavigate={onNavigate} />
        ))}
      </div>
    </div>
  )
}

function ConnectorCard({
  connector,
  onNavigate,
}: {
  connector: ConnectorInfo
  onNavigate: (s: string) => void
}) {
  const [output, setOutput] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function run(actionId: string) {
    setBusy(true)
    try {
      const r = await invokeConnector(connector.id, actionId)
      setOutput(r.result)
    } catch (e) {
      setOutput('Error: ' + String(e))
    } finally {
      setBusy(false)
    }
  }

  const connected = connector.status === 'CONNECTED'
  return (
    <div className="agent-card">
      <div className="mem-top">
        <span className="agent-name">🔌 {connector.name}</span>
        <span className={`chip ${connected ? 'risk-low' : ''}`}>{connector.status}</span>
      </div>
      <div className="muted small agent-role">{connector.category}</div>
      <div className="file-toolbar connector-actions">
        {connector.actions.map((a) => (
          <button key={a.id} disabled={busy} title={a.description} onClick={() => run(a.id)}>
            {a.name}
          </button>
        ))}
      </div>
      {!connected && connector.requiredSecret && (
        <button className="trace-toggle" onClick={() => onNavigate('/secrets')}>
          Connect → store “{connector.requiredSecret}”
        </button>
      )}
      {output && <pre className="json sandbox-output">{output}</pre>}
    </div>
  )
}

const RUN_STATUS_CLASS: Record<string, string> = {
  DONE: 'risk-low',
  RUNNING: 'risk-medium',
  PAUSED: 'risk-medium',
  FAILED: 'sensitive',
  AWAITING_APPROVAL: 'risk-medium',
}

function WorkflowsView({ data, actions }: { data: WorkflowsData; actions: WorkflowActions }) {
  return (
    <div className="result">
      <div className="file-header">
        <span className="muted small">{data.workflows.length} workflows</span>
        <span className="file-toolbar">
          <button onClick={actions.add}>+ New workflow</button>
        </span>
      </div>
      {data.workflows.length === 0 && <div className="muted small">No workflows yet. Click “+ New workflow”.</div>}
      <div className="mem-list">
        {data.workflows.map((w) => (
          <div key={w.id} className="mem-card">
            <div className="mem-top">
              <span className="mem-title">⚙ {w.name}</span>
              <span className="mem-badges">
                <span className="chip">{w.triggerType}</span>
                {w.cron && <span className="chip mono">{w.cron}</span>}
                {w.scheduled && <span className="chip risk-low">scheduled</span>}
              </span>
            </div>
            <div className="mem-meta muted small">
              <span>{w.steps.length} steps: {w.steps.map((s) => s.type).join(' → ')}</span>
            </div>
            <div className="mem-actions">
              <button className="approve-btn" onClick={() => actions.run(w.id)}>
                Run
              </button>
              <button className="link-danger" onClick={() => actions.remove(w)}>
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>
      {data.runs.length > 0 && (
        <>
          <div className="muted small" style={{ marginTop: 14 }}>
            Recent runs
          </div>
          <div className="mem-list">
            {data.runs.slice(0, 10).map((r: RunView) => (
              <div key={r.id} className="run-row">
                <span className={`chip ${RUN_STATUS_CLASS[r.status] ?? ''}`}>{r.status}</span>
                <span className="muted small">{r.trigger}</span>
                <span className="run-steps">
                  {r.steps.map((s) => (
                    <span key={s.stepId} className={`step-dot ${RUN_STATUS_CLASS[s.status] ?? ''}`} title={`${s.name}: ${s.status}`} />
                  ))}
                </span>
                <span className="muted small mono">{new Date(r.startedAt).toLocaleTimeString()}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

const STEP_TYPES: WfStepType[] = ['COMMAND', 'BRAIN', 'CONNECTOR', 'NOTIFY', 'APPROVAL']

function WorkflowEditor({
  state,
  onChange,
  onSave,
  onClose,
}: {
  state: WfEditorState
  onChange: (draft: WorkflowDraft) => void
  onSave: () => void
  onClose: () => void
}) {
  const d = state.draft
  const set = (patch: Partial<WorkflowDraft>) => onChange({ ...d, ...patch })
  const setStep = (i: number, patch: Partial<WfStep>) =>
    set({ steps: d.steps.map((s, idx) => (idx === i ? { ...s, ...patch } : s)) })
  const addStep = () =>
    set({
      steps: [
        ...d.steps,
        { id: 's' + (d.steps.length + 1), name: 'Step ' + (d.steps.length + 1), type: 'COMMAND', config: {}, maxAttempts: 1 },
      ],
    })
  const removeStep = (i: number) => set({ steps: d.steps.filter((_, idx) => idx !== i) })
  const cfg = (s: WfStep, key: string) => (s.config[key] as string) ?? ''
  const setCfg = (i: number, key: string, value: string) =>
    setStep(i, { config: { ...d.steps[i].config, [key]: value } })

  const valid = !!d.name.trim() && d.steps.length > 0

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal mem-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="accent">New workflow</span>
          <div className="modal-actions">
            <button className="primary" disabled={!valid || state.saving} onClick={onSave}>
              {state.saving ? 'Saving…' : 'Create'}
            </button>
            <button onClick={onClose}>Close</button>
          </div>
        </div>
        <div className="mem-form">
          <label>
            Name
            <input value={d.name} onChange={(e) => set({ name: e.target.value })} placeholder="Morning briefing" />
          </label>
          <label>
            Trigger
            <select value={d.triggerType} onChange={(e) => set({ triggerType: e.target.value as WorkflowDraft['triggerType'] })}>
              <option value="MANUAL">Manual</option>
              <option value="SCHEDULE">Schedule (cron)</option>
              <option value="WEBHOOK">Webhook</option>
            </select>
          </label>
          {d.triggerType === 'SCHEDULE' && (
            <label className="full">
              Cron (sec min hour dom mon dow)
              <input value={d.cron ?? ''} onChange={(e) => set({ cron: e.target.value })} placeholder="0 0 9 * * *" />
            </label>
          )}
          <div className="full">
            <div className="file-header">
              <span className="muted small">Steps</span>
              <span className="file-toolbar">
                <button onClick={addStep}>+ Step</button>
              </span>
            </div>
            {d.steps.map((s, i) => (
              <div key={i} className="wf-step">
                <div className="wf-step-head">
                  <input
                    className="wf-step-name"
                    value={s.name}
                    onChange={(e) => setStep(i, { name: e.target.value })}
                  />
                  <select value={s.type} onChange={(e) => setStep(i, { type: e.target.value as WfStepType, config: {} })}>
                    {STEP_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                  <button className="file-del" onClick={() => removeStep(i)}>
                    ✕
                  </button>
                </div>
                {s.type === 'COMMAND' && (
                  <input placeholder="/status" value={cfg(s, 'command')} onChange={(e) => setCfg(i, 'command', e.target.value)} />
                )}
                {s.type === 'BRAIN' && (
                  <input placeholder="summarise my day" value={cfg(s, 'prompt')} onChange={(e) => setCfg(i, 'prompt', e.target.value)} />
                )}
                {s.type === 'NOTIFY' && (
                  <input placeholder="message" value={cfg(s, 'message')} onChange={(e) => setCfg(i, 'message', e.target.value)} />
                )}
                {s.type === 'APPROVAL' && (
                  <input placeholder="why approval is needed" value={cfg(s, 'why')} onChange={(e) => setCfg(i, 'why', e.target.value)} />
                )}
                {s.type === 'CONNECTOR' && (
                  <div className="wf-connector">
                    <input placeholder="connector (e.g. github)" value={cfg(s, 'connector')} onChange={(e) => setCfg(i, 'connector', e.target.value)} />
                    <input placeholder="action (e.g. list_repos)" value={cfg(s, 'action')} onChange={(e) => setCfg(i, 'action', e.target.value)} />
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

function PluginsView({ data, onPick }: { data: PluginsData; onPick: (s: string) => void }) {
  return (
    <div className="result">
      <div className="status-grid">
        {Object.entries(data.counts).map(([k, v]) => (
          <Metric key={k} label={k} value={String(v)} />
        ))}
      </div>
      <div className="muted small" style={{ marginTop: 10 }}>Agents</div>
      <div className="mem-badges">
        {data.agents.map((a) => (
          <span key={a.slug} className="chip" title={a.role}>
            {a.name}
          </span>
        ))}
      </div>
      <div className="muted small" style={{ marginTop: 10 }}>Tools</div>
      <div className="mem-badges">
        {data.tools.map((t) => (
          <span key={t.name} className="chip mono" title={t.description}>
            {t.name}
          </span>
        ))}
      </div>
      <div className="muted small" style={{ marginTop: 10 }}>Connectors</div>
      <div className="mem-badges">
        {data.connectors.map((c) => (
          <span key={c.id} className={`chip ${c.status === 'CONNECTED' ? 'risk-low' : ''}`}>
            {c.name}
          </span>
        ))}
      </div>
      <div className="muted small" style={{ marginTop: 10 }}>Commands ({data.commands.length})</div>
      <div className="mem-badges">
        {data.commands.map((c) => (
          <span key={c.slash} className="chip mono accent" style={{ cursor: 'pointer' }} onClick={() => onPick(c.slash)} title={c.description}>
            {c.slash}
          </span>
        ))}
      </div>
    </div>
  )
}

function ModelsView({ data }: { data: ModelsData }) {
  return (
    <div className="result">
      <div className="muted small">
        Active: <span className="accent">{data.active}</span> · routing: {data.preference}
      </div>
      <table className="data-table">
        <tbody>
          {data.models.map((m) => (
            <tr key={m.id}>
              <td className="mono accent">{m.id}</td>
              <td className="small">{m.local ? 'local' : m.provider}</td>
              <td className="small">q{m.quality}</td>
              <td className="muted small">${m.costInputPer1k}/${m.costOutputPer1k} per 1k</td>
              <td>{m.available ? <span className="chip risk-low">active</span> : <span className="muted small">—</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function DebuggerView({ runs, onReplay }: { runs: RunRecord[]; onReplay: (id: string) => void }) {
  const [open, setOpen] = useState<string | null>(null)
  return (
    <div className="result">
      <div className="muted small">{runs.length} runs</div>
      <div className="mem-list">
        {runs.map((r) => {
          let steps: { kind: string; label: string; detail: string | null }[] = []
          try {
            steps = r.stepsJson ? JSON.parse(r.stepsJson) : []
          } catch {
            steps = []
          }
          const expanded = open === r.id
          return (
            <div key={r.id} className="mem-card">
              <div className="mem-top">
                <span className="mem-title">
                  {r.agent} <span className="muted small mono">{r.model}</span>
                </span>
                <span className="mem-badges">
                  <span className={`chip ${r.status === 'FAILED' ? 'sensitive' : 'risk-low'}`}>{r.status}</span>
                </span>
              </div>
              <div className="mem-content small">{r.request}</div>
              <div className="mem-meta muted small">
                <span>{r.promptTokens + r.completionTokens} tokens</span>
                <span>${r.cost.toFixed(4)}</span>
                <span>{r.durationMs} ms</span>
                <span className="mono">{r.id}</span>
              </div>
              <div className="mem-actions">
                <button onClick={() => setOpen(expanded ? null : r.id)}>{expanded ? 'hide trace' : 'trace'}</button>
                <button onClick={() => onReplay(r.id)}>Replay</button>
              </div>
              {expanded && (
                <ol className="trace">
                  {steps.map((s, i) => (
                    <li key={i}>
                      <span className="trace-icon">{STEP_ICON[s.kind] ?? '•'}</span>
                      <span className="trace-label">{s.label}</span>
                      {s.detail && <span className="trace-detail muted small">{s.detail}</span>}
                    </li>
                  ))}
                </ol>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

function CostsView({ data }: { data: CostsData }) {
  return (
    <div className="result">
      <div className="status-grid">
        <Metric label="Runs" value={String(data.runs)} />
        <Metric label="Total tokens" value={String(data.totalTokens)} />
        <Metric label="Total cost" value={`$${data.totalCost.toFixed(4)}`} good />
        <Metric label="Prompt / completion" value={`${data.promptTokens} / ${data.completionTokens}`} />
      </div>
      <div className="muted small" style={{ marginTop: 10 }}>Cost by model</div>
      <table className="data-table">
        <tbody>
          {Object.entries(data.costByModel).map(([m, c]) => (
            <tr key={m}>
              <td className="mono accent">{m}</td>
              <td>${Number(c).toFixed(4)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="muted small" style={{ marginTop: 8 }}>Runs by agent</div>
      <table className="data-table">
        <tbody>
          {Object.entries(data.runsByAgent).map(([a, n]) => (
            <tr key={a}>
              <td>{a}</td>
              <td>{String(n)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function KbView({
  data,
  onSearch,
  onIndex,
  onRemove,
}: {
  data: KbData
  onSearch: (q: string) => void
  onIndex: () => void
  onRemove: (id: string) => void
}) {
  const [q, setQ] = useState(data.query || '')
  return (
    <div className="result">
      <div className="file-header">
        <span className="muted small">{data.documents.length} documents indexed</span>
        <span className="file-toolbar">
          <button onClick={onIndex}>+ Index a file/folder</button>
        </span>
      </div>
      <form
        className="kb-search"
        onSubmit={(e) => {
          e.preventDefault()
          if (q.trim()) onSearch(q.trim())
        }}
      >
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Semantic search your documents…" />
        <button type="submit">Search</button>
      </form>

      {data.results.length > 0 && (
        <div className="kb-results">
          {data.results.map((h: KbHit, i) => (
            <div key={i} className="kb-hit">
              <div className="kb-hit-head">
                <span className="accent">{h.title}</span>
                <span className="chip">{(h.score * 100).toFixed(0)}%</span>
              </div>
              <div className="small">{h.content.length > 240 ? h.content.slice(0, 240) + '…' : h.content}</div>
              <div className="muted small mono">{h.source}</div>
            </div>
          ))}
        </div>
      )}

      <div className="mem-list" style={{ marginTop: 10 }}>
        {data.documents.map((d) => (
          <div key={d.id} className="file-row">
            <span className="file-main" style={{ cursor: 'default' }}>
              <span className="file-icon">📑</span>
              <span className="file-name">{d.title ?? d.source}</span>
              <span className="muted small">{d.chunkCount} chunks</span>
            </span>
            <button className="file-del" title="Remove" onClick={() => onRemove(d.id)}>
              ✕
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

function NotificationBell({ online }: { online: boolean }) {
  const [unread, setUnread] = useState(0)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<NotificationItem[]>([])

  useEffect(() => {
    if (!online) return
    let active = true
    const poll = () => getUnreadCount().then((n) => active && setUnread(n)).catch(() => {})
    poll()
    const id = window.setInterval(poll, 8000)
    return () => {
      active = false
      window.clearInterval(id)
    }
  }, [online])

  async function toggle() {
    const next = !open
    setOpen(next)
    if (next) {
      try {
        setItems(await getNotifications())
        await markNotificationsRead()
        setUnread(0)
      } catch {
        /* offline */
      }
    }
  }

  return (
    <div className="bell-wrap">
      <button className="bell" onClick={toggle} title="Notifications">
        🔔{unread > 0 && <span className="bell-badge">{unread > 9 ? '9+' : unread}</span>}
      </button>
      {open && (
        <div className="bell-dropdown" onClick={(e) => e.stopPropagation()}>
          <div className="bell-head muted small">Notifications</div>
          {items.length === 0 && <div className="muted small bell-empty">Nothing yet.</div>}
          {items.map((n) => (
            <div key={n.id} className={`bell-item ${n.read ? '' : 'unread'}`}>
              <span className={`step-dot ${n.type === 'error' ? 'sensitive' : n.type === 'success' ? 'risk-low' : 'risk-medium'}`} />
              <div className="bell-text">
                <div className="bell-title">{n.title}</div>
                {n.body && <div className="muted small">{n.body}</div>}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function RiskBadge({ level }: { level: RiskLevel }) {
  return <span className={`chip risk-${level.toLowerCase()}`}>{level}</span>
}

function ApprovalPending({
  request,
  message,
  onOpen,
}: {
  request: ApprovalRequest
  message: string
  onOpen: () => void
}) {
  return (
    <div className="result approval-pending">
      <div className="mem-top">
        <span className="mem-title">⏳ {message}</span>
        <RiskBadge level={request.riskLevel} />
      </div>
      <pre className="json">{request.preview}</pre>
      <button className="welcome-cmds-btn" onClick={onOpen}>
        Open Approval Center
      </button>
    </div>
  )
}

function ApprovalView({ requests, actions }: { requests: ApprovalRequest[]; actions: ApprovalActions }) {
  return (
    <div className="result">
      <div className="muted small">{requests.length} pending</div>
      {requests.length === 0 && <div className="muted small">Nothing awaiting approval.</div>}
      <div className="mem-list">
        {requests.map((r) => (
          <ApprovalCard key={r.id} request={r} actions={actions} />
        ))}
      </div>
    </div>
  )
}

function ApprovalCard({ request, actions }: { request: ApprovalRequest; actions: ApprovalActions }) {
  const [remember, setRemember] = useState(false)
  return (
    <div className="mem-card">
      <div className="mem-top">
        <span className="mem-title">{request.title}</span>
        <RiskBadge level={request.riskLevel} />
      </div>
      {request.description && <div className="mem-content muted">{request.description}</div>}
      {request.preview && <pre className="json">{request.preview}</pre>}
      <div className="mem-actions approval-actions">
        <button className="approve-btn" onClick={() => actions.approve(request.id, remember)}>
          Approve
        </button>
        <button className="link-danger" onClick={() => actions.deny(request.id, remember)}>
          Deny
        </button>
        <label className="remember">
          <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
          Remember
        </label>
      </div>
    </div>
  )
}

function SandboxView({ result }: { result: SandboxResult }) {
  const ok = result.exitCode === 0 && !result.timedOut
  return (
    <div className="result">
      <div className="file-header">
        <span className="muted small">
          Sandbox · {result.durationMs} ms · {result.timedOut ? 'timed out' : `exit ${result.exitCode}`}
        </span>
        <span className={`chip ${ok ? 'risk-low' : 'sensitive'}`}>{ok ? 'success' : 'failed'}</span>
      </div>
      <pre className="json sandbox-output">{result.output || '(no output)'}</pre>
    </div>
  )
}

function SecretsView({ secrets, actions }: { secrets: SecretView[]; actions: SecretActions }) {
  return (
    <div className="result">
      <div className="file-header">
        <span className="muted small">{secrets.length} secrets · encrypted at rest, never shown to the model</span>
        <span className="file-toolbar">
          <button onClick={actions.add}>+ Add</button>
        </span>
      </div>
      {secrets.length === 0 && <div className="muted small">Vault is empty.</div>}
      <div className="mem-list">
        {secrets.map((s) => (
          <div key={s.id} className="mem-card">
            <div className="mem-top">
              <span className="mem-title">
                🔑 {s.name} <span className="mono accent">{s.masked}</span>
              </span>
              <span className="mem-badges">
                {s.connector && <span className="chip">{s.connector}</span>}
                {s.scopes.map((sc) => (
                  <span key={sc} className="chip">
                    {sc}
                  </span>
                ))}
              </span>
            </div>
            <div className="mem-meta muted small">
              <span className="mono">{s.id}</span>
              {s.lastAccessedAt && <span>last used: {new Date(s.lastAccessedAt).toLocaleString()}</span>}
            </div>
            <div className="mem-actions">
              <button className="link-danger" onClick={() => actions.remove(s)}>
                Revoke
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function SecretEditor({
  state,
  onChange,
  onSave,
  onClose,
}: {
  state: SecretEditorState
  onChange: (draft: SecretDraft) => void
  onSave: () => void
  onClose: () => void
}) {
  const d = state.draft
  const set = (patch: Partial<SecretDraft>) => onChange({ ...d, ...patch })
  const valid = !!d.name.trim() && !!d.value.trim()
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal mem-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="accent">Add secret</span>
          <div className="modal-actions">
            <button className="primary" disabled={!valid || state.saving} onClick={onSave}>
              {state.saving ? 'Saving…' : 'Store'}
            </button>
            <button onClick={onClose}>Close</button>
          </div>
        </div>
        <div className="mem-form">
          <label>
            Name
            <input value={d.name} onChange={(e) => set({ name: e.target.value })} placeholder="github-token" />
          </label>
          <label>
            Connector
            <input value={d.connector ?? ''} onChange={(e) => set({ connector: e.target.value })} placeholder="github" />
          </label>
          <label className="full">
            Value (encrypted on save)
            <input type="password" value={d.value} onChange={(e) => set({ value: e.target.value })} />
          </label>
          <label className="full">
            Scopes (comma-separated)
            <input
              value={(d.scopes ?? []).join(',')}
              onChange={(e) => set({ scopes: e.target.value.split(',').map((s) => s.trim()).filter(Boolean) })}
              placeholder="repo, read:user"
            />
          </label>
        </div>
      </div>
    </div>
  )
}

function MemoryView({ memories, actions }: { memories: Memory[]; actions: MemoryActions }) {
  return (
    <div className="result">
      <div className="file-header">
        <span className="muted small">{memories.length} memories</span>
        <span className="file-toolbar">
          <button onClick={actions.add}>+ Add</button>
          <button onClick={actions.exportAll}>Export</button>
        </span>
      </div>
      {memories.length === 0 && <div className="muted small">No memories yet. Click “+ Add”.</div>}
      <div className="mem-list">
        {memories.map((m) => (
          <div key={m.id} className={`mem-card ${m.enabled ? '' : 'disabled'}`}>
            <div className="mem-top">
              <span className="mem-title">{m.title}</span>
              <span className="mem-badges">
                <span className="chip">{m.category}</span>
                {m.sensitivity === 'SENSITIVE' && <span className="chip sensitive">sensitive</span>}
                {!m.enabled && <span className="chip">disabled</span>}
              </span>
            </div>
            <div className="mem-content">{m.content}</div>
            <div className="mem-meta muted small">
              <span>source: {m.source ?? '—'}</span>
              <span>confidence: {(m.confidence * 100).toFixed(0)}%</span>
              {m.expiresAt && <span>expires: {new Date(m.expiresAt).toLocaleDateString()}</span>}
              <span className="mono">{m.id}</span>
            </div>
            <div className="mem-actions">
              <button onClick={() => actions.edit(m)}>Edit</button>
              <button className="link-danger" onClick={() => actions.remove(m)}>
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function HelpView({ commands, onPick }: { commands: CommandDefinition[]; onPick: (s: string) => void }) {
  return (
    <div className="result">
      <table className="data-table">
        <tbody>
          {commands.map((c) => (
            <tr key={c.slash} onClick={() => onPick(c.slash)}>
              <td className="mono accent">{c.slash}</td>
              <td>{c.description}</td>
              <td className="muted small">{c.category}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function FilesView({
  data,
  onNavigate,
  actions,
}: {
  data: { path: string; entries: FileNode[] }
  onNavigate: (s: string) => void
  actions: FileActions
}) {
  const parent = data.path ? data.path.split('/').slice(0, -1).join('/') : null
  return (
    <div className="result">
      <div className="file-header">
        <span className="file-path mono muted">/{data.path}</span>
        <span className="file-toolbar">
          <button onClick={() => actions.newFile(data.path)}>+ File</button>
          <button onClick={() => actions.newFolder(data.path)}>+ Folder</button>
        </span>
      </div>
      <div className="file-list">
        {parent !== null && (
          <div className="file-row">
            <button className="file-main" onClick={() => onNavigate(`/jfiles ${parent}`)}>
              <span className="file-icon">↩</span> ..
            </button>
          </div>
        )}
        {data.entries.length === 0 && <div className="muted small">empty folder</div>}
        {data.entries.map((f) => (
          <div key={f.path} className="file-row">
            <button
              className="file-main"
              onClick={() => (f.directory ? onNavigate(`/jfiles ${f.path}`) : actions.open(f.path))}
            >
              <span className="file-icon">{f.directory ? '📁' : '📄'}</span>
              <span className="file-name">{f.name}</span>
              {!f.directory && <span className="file-size muted small">{formatBytes(f.size)}</span>}
            </button>
            <button className="file-del" title="Delete" onClick={() => actions.remove(f.path, data.path)}>
              ✕
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

function StatusView({ data }: { data: Record<string, unknown> }) {
  const cpu = data.cpu as Record<string, number>
  const mem = data.memory as Record<string, number>
  const disk = data.disk as Record<string, number>
  return (
    <div className="result status-grid">
      <Metric label="OS" value={String(data.os)} />
      <Metric label="Health" value={String(data.jarvisHealth)} good />
      <Metric label="CPU cores" value={String(cpu.availableProcessors)} />
      <Metric label="System load" value={fmtPct(cpu.systemCpuLoad)} />
      <Metric
        label="RAM used"
        value={`${formatBytes(mem.usedPhysicalBytes)} / ${formatBytes(mem.totalPhysicalBytes)}`}
      />
      <Metric
        label="Disk free"
        value={`${formatBytes(disk.freeBytes)} / ${formatBytes(disk.totalBytes)}`}
      />
    </div>
  )
}

function ResourcesView({ initial }: { initial: Record<string, unknown> }) {
  const [snap, setSnap] = useState<Record<string, unknown>>(initial)
  const [live, setLive] = useState(false)
  const [cpuHist, setCpuHist] = useState<number[]>([])
  const [memHist, setMemHist] = useState<number[]>([])

  useEffect(() => {
    const es = new EventSource('/api/monitor/stream')
    es.addEventListener('snapshot', (e) => {
      const data = JSON.parse((e as MessageEvent).data) as Record<string, unknown>
      setSnap(data)
      setLive(true)
      const cpu = (data.cpu as Record<string, number>).systemCpuLoad
      const mem = data.memory as Record<string, number>
      const memPct = mem.usedPhysicalBytes / mem.totalPhysicalBytes
      setCpuHist((h) => [...h, cpu < 0 ? 0 : cpu].slice(-40))
      setMemHist((h) => [...h, memPct].slice(-40))
    })
    es.onerror = () => setLive(false)
    es.onopen = () => setLive(true)
    return () => es.close()
  }, [])

  const cpu = snap.cpu as Record<string, number>
  const mem = snap.memory as Record<string, number>
  const swap = snap.swap as Record<string, number>
  const disk = snap.disk as Record<string, number>
  const proc = snap.process as Record<string, number>
  const jvm = snap.jvm as Record<string, number | string>
  const rt = snap.runtime as Record<string, number | string>

  return (
    <div className="result">
      <div className="file-header">
        <span className="muted small">Live System Monitor</span>
        <span className={`live-badge ${live ? 'on' : 'off'}`}>
          <span className="status-dot" /> {live ? 'live' : 'reconnecting…'}
        </span>
      </div>

      <div className="status-grid">
        <Metric label="Health" value={String(snap.jarvisHealth)} good />
        <Metric label="OS" value={String(snap.os)} />
        <Metric label="CPU cores" value={String(cpu.availableProcessors)} />
        <Metric label="Load avg" value={String(cpu.systemLoadAverage)} />
        <Metric label="Active agents" value={String(rt.activeAgents)} />
        <Metric label="Running tasks" value={String(rt.runningTasks)} />
        <Metric label="Threads" value={String(proc.threads)} />
        <Metric
          label="Open files"
          value={proc.openFileDescriptors != null ? `${proc.openFileDescriptors} / ${proc.maxFileDescriptors}` : 'n/a'}
        />
        <Metric label="Uptime" value={fmtDuration(Number(jvm.uptimeMillis))} />
      </div>

      <div className="gauge-grid">
        <Gauge
          label="CPU"
          pct={cpu.systemCpuLoad < 0 ? 0 : cpu.systemCpuLoad}
          caption={fmtPct(cpu.systemCpuLoad)}
          history={cpuHist}
        />
        <Gauge
          label="RAM"
          pct={mem.usedPhysicalBytes / mem.totalPhysicalBytes}
          caption={`${formatBytes(mem.usedPhysicalBytes)} / ${formatBytes(mem.totalPhysicalBytes)}`}
          history={memHist}
        />
        <Gauge
          label="Swap"
          pct={swap.totalBytes ? swap.usedBytes / swap.totalBytes : 0}
          caption={`${formatBytes(swap.usedBytes)} / ${formatBytes(swap.totalBytes)}`}
        />
        <Gauge
          label="Disk"
          pct={disk.totalBytes ? (disk.totalBytes - disk.freeBytes) / disk.totalBytes : 0}
          caption={`${formatBytes(disk.freeBytes)} free`}
        />
      </div>
    </div>
  )
}

function Gauge({
  label,
  pct,
  caption,
  history,
}: {
  label: string
  pct: number
  caption: string
  history?: number[]
}) {
  const clamped = Math.max(0, Math.min(1, pct))
  return (
    <div className="gauge">
      <div className="gauge-top">
        <span className="gauge-label">{label}</span>
        <span className="gauge-pct mono">{(clamped * 100).toFixed(0)}%</span>
      </div>
      <div className="gauge-bar">
        <div className="gauge-fill" style={{ width: `${clamped * 100}%` }} />
      </div>
      {history && history.length > 1 && <Sparkline values={history} />}
      <div className="gauge-caption muted small">{caption}</div>
    </div>
  )
}

function Sparkline({ values }: { values: number[] }) {
  const w = 200
  const h = 32
  const max = Math.max(0.001, ...values)
  const step = values.length > 1 ? w / (values.length - 1) : w
  const points = values
    .map((v, i) => `${(i * step).toFixed(1)},${(h - (v / max) * h).toFixed(1)}`)
    .join(' ')
  return (
    <svg className="sparkline" viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none">
      <polyline points={points} fill="none" stroke="var(--accent)" strokeWidth="1.5" />
    </svg>
  )
}

function fmtDuration(ms: number): string {
  const s = Math.floor(ms / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m ${s % 60}s`
  return `${s}s`
}

function LogsView({ entries }: { entries: AuditEntry[] }) {
  return (
    <div className="result">
      <table className="data-table">
        <tbody>
          {entries.map((e) => (
            <tr key={e.id}>
              <td className="muted small mono">{new Date(e.timestamp).toLocaleTimeString()}</td>
              <td className="small">{e.command ?? e.inputType}</td>
              <td className={`small ${e.status === 'ERROR' ? 'danger' : 'accent'}`}>{e.status}</td>
              <td className="muted small">{e.detail}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function Metric({ label, value, good }: { label: string; value: string; good?: boolean }) {
  return (
    <div className="metric">
      <div className="metric-label">{label}</div>
      <div className={`metric-value ${good ? 'accent' : ''}`}>{value}</div>
    </div>
  )
}

function JsonView({ label, data }: { label: string; data: unknown }) {
  return (
    <div className="result">
      <div className="muted small">{label}</div>
      <pre className="json">{JSON.stringify(data, null, 2)}</pre>
    </div>
  )
}

function Editor({
  editor,
  onChange,
  onSave,
  onClose,
  onReveal,
}: {
  editor: EditorState
  onChange: (content: string) => void
  onSave: () => void
  onClose: () => void
  onReveal: () => void
}) {
  const dirty = editor.content !== editor.original
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="mono accent">{editor.path}</span>
          <div className="modal-actions">
            <button title="Reveal in Finder" onClick={onReveal}>
              📂 Open location
            </button>
            <button className="primary" disabled={!dirty || editor.saving} onClick={onSave}>
              {editor.saving ? 'Saving…' : dirty ? 'Save' : 'Saved'}
            </button>
            <button onClick={onClose}>Close</button>
          </div>
        </div>
        <textarea
          className="editor-area mono"
          value={editor.content}
          spellCheck={false}
          onChange={(e) => onChange(e.target.value)}
        />
      </div>
    </div>
  )
}

function MemoryEditor({
  state,
  onChange,
  onSave,
  onClose,
}: {
  state: MemEditorState
  onChange: (draft: MemoryDraft) => void
  onSave: () => void
  onClose: () => void
}) {
  const d = state.draft
  const set = (patch: Partial<MemoryDraft>) => onChange({ ...d, ...patch })
  const valid = !!d.title?.trim() && !!d.content?.trim() && !!d.category?.trim()
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal mem-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <span className="accent">{state.id ? 'Edit memory' : 'New memory'}</span>
          <div className="modal-actions">
            <button className="primary" disabled={!valid || state.saving} onClick={onSave}>
              {state.saving ? 'Saving…' : 'Save'}
            </button>
            <button onClick={onClose}>Close</button>
          </div>
        </div>
        <div className="mem-form">
          <label>
            Title
            <input value={d.title ?? ''} onChange={(e) => set({ title: e.target.value })} />
          </label>
          <label>
            Category
            <input value={d.category ?? ''} onChange={(e) => set({ category: e.target.value })} />
          </label>
          <label className="full">
            Content
            <textarea
              rows={4}
              value={d.content ?? ''}
              onChange={(e) => set({ content: e.target.value })}
            />
          </label>
          <label>
            Source
            <input value={d.source ?? ''} onChange={(e) => set({ source: e.target.value })} />
          </label>
          <label>
            Confidence ({Math.round((d.confidence ?? 1) * 100)}%)
            <input
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={d.confidence ?? 1}
              onChange={(e) => set({ confidence: Number(e.target.value) })}
            />
          </label>
          <label>
            Sensitivity
            <select
              value={d.sensitivity ?? 'NORMAL'}
              onChange={(e) => set({ sensitivity: e.target.value as MemoryDraft['sensitivity'] })}
            >
              <option value="NORMAL">Normal</option>
              <option value="SENSITIVE">Sensitive</option>
            </select>
          </label>
          <label>
            Visibility
            <select
              value={d.visibility ?? 'USER_VISIBLE'}
              onChange={(e) => set({ visibility: e.target.value as MemoryDraft['visibility'] })}
            >
              <option value="USER_VISIBLE">User-visible</option>
              <option value="INTERNAL">Internal</option>
            </select>
          </label>
          <label>
            Expires
            <input
              type="datetime-local"
              value={toLocalInput(d.expiresAt)}
              onChange={(e) =>
                set({ expiresAt: e.target.value ? new Date(e.target.value).toISOString() : null })
              }
            />
          </label>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={d.enabled ?? true}
              onChange={(e) => set({ enabled: e.target.checked })}
            />
            Enabled
          </label>
        </div>
      </div>
    </div>
  )
}

function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return ''
  const dt = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}T${pad(dt.getHours())}:${pad(dt.getMinutes())}`
}

function groupByCategory(commands: CommandDefinition[]): Record<string, CommandDefinition[]> {
  return commands.reduce<Record<string, CommandDefinition[]>>((acc, c) => {
    ;(acc[c.category] ??= []).push(c)
    return acc
  }, {})
}

function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(i ? 1 : 0)} ${units[i]}`
}

function fmtPct(load: number): string {
  return load < 0 ? 'n/a' : `${(load * 100).toFixed(1)}%`
}
