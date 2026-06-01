import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  chat, fetchCommands, getAgents, getAudit, getMemoryList, getNotifications,
  getRuns, getSettings, getStatus, getTasks, getUnreadCount, markNotificationsRead,
  runCommand, setProvider as apiSetProvider,
} from './api'
import type {
  AgentDef, AuditEntry, ChatResponse, CommandDefinition, Memory, MonitorSnapshot,
  NotificationItem, RunRecord, SettingsView, Step, TaskItem,
} from './api'

/* eslint-disable @typescript-eslint/no-explicit-any */
const SR: any = typeof window !== 'undefined' ? (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition : null
function speak(text: string) {
  try {
    const u = new SpeechSynthesisUtterance(text.slice(0, 600))
    const v = speechSynthesis.getVoices().find((x) => /daniel|arthur|google uk english male/i.test(x.name))
    if (v) u.voice = v
    u.rate = 1.02; u.pitch = 1
    speechSynthesis.cancel(); speechSynthesis.speak(u)
  } catch { /* speech not available */ }
}

/* ===========================================================================
   Helpers
=========================================================================== */
function pct(v?: number) { return v === undefined || v === null || v < 0 || Number.isNaN(v) ? 0 : Math.round(v * 100) }
function gb(b?: number) { return !b ? '0' : (b / 1024 ** 3).toFixed(1) }
function ago(iso?: string | null) {
  if (!iso) return ''
  const d = Date.parse(iso); if (Number.isNaN(d)) return ''
  const s = Math.max(0, (Date.now() - d) / 1000)
  if (s < 60) return `${Math.floor(s)}s`
  if (s < 3600) return `${Math.floor(s / 60)}m`
  if (s < 86400) return `${Math.floor(s / 3600)}h`
  return `${Math.floor(s / 86400)}d`
}

/* ===========================================================================
   Sphere — three fresh concepts (line/glow, no blob, no particle burst)
=========================================================================== */
type SphereKind = 'gyro' | 'orbital' | 'halo'
const CORE_GLOW = { filter: 'drop-shadow(0 0 6px var(--accent))' }

function Sphere({ kind, busy, caption }: { kind: SphereKind; busy: boolean; caption: string }) {
  return (
    <div className={`orb${busy ? ' busy' : ''}`}>
      <svg viewBox="0 0 200 200" aria-hidden>
        {kind === 'gyro' && <Gyro />}
        {kind === 'orbital' && <Orbital />}
        {kind === 'halo' && <Halo />}
      </svg>
      <div className="caption">{caption}</div>
    </div>
  )
}

/* V1 — gyroscopic reticle: broken arcs at tilts + faint wire-globe + calm core */
function Gyro() {
  const merid = [
    { rx: 56, ry: 56 }, { rx: 44, ry: 56 }, { rx: 28, ry: 56 }, { rx: 12, ry: 56 },
    { rx: 56, ry: 44 }, { rx: 56, ry: 28 }, { rx: 56, ry: 12 },
  ]
  return (
    <g stroke="currentColor">
      <g className="spin-cw">
        <circle className="stroke" cx="100" cy="100" r="94" strokeWidth="1" strokeDasharray="200 40 70 30" opacity="0.7" />
      </g>
      <g className="spin-ccw">
        <ellipse className="stroke" cx="100" cy="100" rx="82" ry="34" strokeWidth="1" strokeDasharray="120 30" opacity="0.55" transform="rotate(32 100 100)" />
      </g>
      <g className="spin-mid">
        <ellipse className="stroke" cx="100" cy="100" rx="72" ry="26" strokeWidth="1" opacity="0.4" transform="rotate(-42 100 100)" />
      </g>
      <g className="spin-cw" opacity="0.22">
        {merid.map((m, i) => <ellipse key={i} className="stroke" cx="100" cy="100" rx={m.rx} ry={m.ry} strokeWidth="0.8" />)}
        <circle className="stroke" cx="100" cy="100" r="56" strokeWidth="0.8" />
      </g>
      <circle className="core" cx="100" cy="100" r="6.5" fill="currentColor" style={CORE_GLOW} />
      <circle className="core" cx="100" cy="100" r="15" fill="none" stroke="currentColor" strokeWidth="1" opacity="0.5" />
    </g>
  )
}

/* V2 — orrery: thin globe + 3 tilted rings with nodes, slow precession */
function Orbital() {
  const rings = [0, 60, 120]
  return (
    <g stroke="currentColor">
      <circle className="stroke" cx="100" cy="100" r="60" strokeWidth="0.8" opacity="0.25" />
      <g className="spin-cw">
        {rings.map((deg, i) => (
          <g key={i} transform={`rotate(${deg} 100 100)`} opacity="0.6">
            <ellipse className="stroke" cx="100" cy="100" rx="86" ry="30" strokeWidth="1" />
            <circle cx="186" cy="100" r="3.2" fill="currentColor" style={CORE_GLOW} transform={`rotate(${deg} 100 100)`} />
          </g>
        ))}
      </g>
      <circle className="core" cx="100" cy="100" r="40" fill="currentColor" opacity="0.06" />
      <circle className="core" cx="100" cy="100" r="7" fill="currentColor" style={CORE_GLOW} />
    </g>
  )
}

/* V3 — halo: single luminous ring + traveling arc + soft core (most minimal) */
function Halo() {
  return (
    <g stroke="currentColor">
      <circle className="stroke" cx="100" cy="100" r="80" strokeWidth="1.2" opacity="0.32" />
      <g className="spin-cw">
        <circle className="stroke" cx="100" cy="100" r="80" strokeWidth="2" strokeLinecap="round" strokeDasharray="70 432" opacity="0.95" />
      </g>
      <g className="spin-ccw">
        <circle className="stroke" cx="100" cy="100" r="64" strokeWidth="1" strokeDasharray="24 400" opacity="0.6" />
      </g>
      <circle className="core" cx="100" cy="100" r="44" fill="currentColor" opacity="0.05" />
      <circle className="core" cx="100" cy="100" r="6" fill="currentColor" style={CORE_GLOW} />
    </g>
  )
}

/* ===========================================================================
   Floating window (draggable)
=========================================================================== */
type WinKind = 'today' | 'memory' | 'history' | 'settings' | 'agents' | 'logs' | 'result' | 'response'
interface Win { key: string; kind: WinKind; title: string; subtitle: string; dim: string; x: number; y: number; z: number; payload?: unknown }

function FloatingWindow({ win, onClose, onFocus, children }: { win: Win; onClose: () => void; onFocus: () => void; children: React.ReactNode }) {
  const [pos, setPos] = useState({ x: win.x, y: win.y })
  const drag = useRef<{ dx: number; dy: number } | null>(null)
  const onDown = (e: React.PointerEvent) => {
    onFocus()
    drag.current = { dx: e.clientX - pos.x, dy: e.clientY - pos.y }
    const move = (ev: PointerEvent) => { if (drag.current) setPos({ x: ev.clientX - drag.current.dx, y: ev.clientY - drag.current.dy }) }
    const up = () => { drag.current = null; window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up) }
    window.addEventListener('pointermove', move); window.addEventListener('pointerup', up)
  }
  const [size] = win.dim.split('×').map(Number)
  return (
    <div className="window fade-in" style={{ left: pos.x, top: pos.y, zIndex: win.z, width: size }} onMouseDown={onFocus}>
      <div className="win-head" onPointerDown={onDown}>
        <span className="pip" />
        <span className="title">{win.title}</span>
        <span className="subtitle">{win.subtitle}</span>
        <span className="dim">{win.dim}</span>
        <button className="close" onClick={onClose}>✕</button>
      </div>
      <div className="win-body" style={{ maxHeight: 'min(70vh, 620px)' }}>{children}</div>
      <div className="win-resize" />
    </div>
  )
}

/* ---- Window contents ----------------------------------------------------- */

function TodayWindow() {
  const [text, setText] = useState<string | null>(null)
  useEffect(() => { runCommand('/today').then((r) => setText(r.message)).catch((e) => setText(String(e))) }, [])
  return (
    <div className="digest-card">
      <div className="digest-head">
        <div><div className="t">✦ JARVIS TODAY</div><div className="s">What Jarvis has done recently</div></div>
        <div className="w-tabs"><span className="w-tab on">24h</span><span className="w-tab">7d</span></div>
      </div>
      <div className="digest">{text === null ? <div className="w-empty"><span className="spin-fast">◠</span></div> : <pre>{text}</pre>}</div>
    </div>
  )
}

function MemoryWindow() {
  const [items, setItems] = useState<Memory[] | null>(null)
  const load = useCallback(() => getMemoryList().then(setItems).catch(() => setItems([])), [])
  useEffect(() => { load() }, [load])
  return (
    <>
      <div className="w-head-row"><span className="w-section-title">Trusted memory</span>
        <button className="btn-soft" onClick={load}>⟳ Scan now</button></div>
      {!items ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : items.length === 0 ? <div className="w-empty"><div className="big">◈</div><div className="s">No memory yet. Tell Jarvis things to remember, or run “Scan now” to extract from recent chats.</div></div>
        : <div className="rows">{items.map((m) => (
            <div className="row" key={m.id}><span className="grow"><strong>{m.title}</strong> — <span style={{ color: 'var(--muted)' }}>{m.content}</span></span><span className="pill low">{m.category}</span></div>
          ))}</div>}
    </>
  )
}

function runState(status: string): 'success' | 'failed' | 'running' {
  const s = status?.toUpperCase()
  if (s === 'ERROR' || s === 'FAILED') return 'failed'
  if (s === 'RUNNING' || s === 'PENDING') return 'running'
  return 'success'
}
function parseSteps(json: string | null): Step[] {
  if (!json) return []
  try { const v = JSON.parse(json); return Array.isArray(v) ? v as Step[] : [] } catch { return [] }
}

function RunRow({ run }: { run: RunRecord }) {
  const [open, setOpen] = useState(false)
  const steps = useMemo(() => parseSteps(run.stepsJson), [run.stepsJson])
  const st = runState(run.status)
  return (
    <div className={`run-row${open ? ' open' : ''}`}>
      <div className="head" onClick={() => setOpen((o) => !o)}>
        <span className="chev">▶</span>
        <span className="grow" style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{run.request}</span>
        <span className="mono" style={{ color: 'var(--muted)', fontSize: 10 }}>{run.agent}</span>
        <span className={`pill ${st === 'success' ? 'done' : st === 'failed' ? 'failed' : 'running'}`}>{run.status}</span>
        <span className="when">{ago(run.createdAt)}</span>
      </div>
      {open && (
        <div className="substeps">
          {steps.length === 0 && <div className="substep"><span className="det">No recorded sub-steps.</span></div>}
          {steps.map((s, i) => (
            <div className="substep" key={i}><span className="kind">{s.kind}</span><span className="lbl">{s.label}{s.detail ? <span className="det"> — {s.detail}</span> : null}</span></div>
          ))}
          {run.answer && <div className="substep"><span className="kind">answer</span><span className="lbl" style={{ whiteSpace: 'pre-wrap' }}>{run.answer.slice(0, 400)}</span></div>}
        </div>
      )}
    </div>
  )
}

function HistoryWindow() {
  const [runs, setRuns] = useState<RunRecord[] | null>(null)
  const [tab, setTab] = useState<'all' | 'running' | 'success' | 'failed'>('all')
  const [q, setQ] = useState('')
  useEffect(() => { getRuns(60).then(setRuns).catch(() => setRuns([])) }, [])
  const filtered = (runs ?? []).filter((r) => (tab === 'all' || runState(r.status) === tab) && r.request.toLowerCase().includes(q.toLowerCase()))
  return (
    <>
      <div className="w-search">🔍 <input placeholder="Filter by prompt text…" value={q} onChange={(e) => setQ(e.target.value)} />
        <div className="w-tabs">{(['all', 'running', 'success', 'failed'] as const).map((t) => <button key={t} className={`w-tab${tab === t ? ' on' : ''}`} onClick={() => setTab(t)}>{t.toUpperCase()}</button>)}</div>
        <span className="mono" style={{ color: 'var(--muted)', fontSize: 11 }}>{filtered.length}/{runs?.length ?? 0}</span>
      </div>
      {!runs ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : filtered.length === 0 ? <div className="w-empty"><div className="t">No prompts yet</div><div className="s">Ask Jarvis something — like “take a screenshot and turn it into a PDF” — and the sub-step tree shows up here.</div></div>
        : <div className="rows">{filtered.map((r) => <RunRow key={r.id} run={r} />)}</div>}
    </>
  )
}

function AgentsWindow() {
  const [agents, setAgents] = useState<AgentDef[] | null>(null)
  useEffect(() => { getAgents().then(setAgents).catch(() => setAgents([])) }, [])
  return !agents ? <div className="w-empty"><span className="spin-fast">◠</span></div>
    : <div className="rows">{agents.map((a) => (
        <div className="row" key={a.slug}><span className="grow"><strong>{a.name}</strong> — <span style={{ color: 'var(--muted)' }}>{a.role}</span></span>
          <span className="pill low">{a.category}</span></div>
      ))}</div>
}

function LogsWindow() {
  const [logs, setLogs] = useState<AuditEntry[] | null>(null)
  useEffect(() => { getAudit(60).then(setLogs).catch(() => setLogs([])) }, [])
  return !logs ? <div className="w-empty"><span className="spin-fast">◠</span></div>
    : logs.length === 0 ? <div className="w-empty"><div className="s">No activity logged yet.</div></div>
    : <div className="rows">{logs.map((l) => (
        <div className="row" key={l.id}><span className={`dot-s ${l.status === 'OK' ? 'ok' : l.status === 'ERROR' ? 'bad' : 'warn'}`} />
          <span className="grow">{l.command || l.inputType}{l.input ? ` — ${l.input}` : ''}</span><span className="when">{ago(l.timestamp)}</span></div>
      ))}</div>
}

function pref(key: string, fallback: string): string {
  try { return localStorage.getItem(key) ?? fallback } catch { return fallback }
}
function savePref(key: string, value: string) { try { localStorage.setItem(key, value) } catch { /* ignore */ } }

function SettingsWindow() {
  const [settings, setSettings] = useState<SettingsView | null>(null)
  const [provider, setProviderState] = useState(pref('jarvis.provider', 'mock'))
  const [model, setModel] = useState('')
  const [stt, setStt] = useState(pref('jarvis.stt', 'whisper'))
  const [tts, setTts] = useState(pref('jarvis.tts', 'system'))
  const [lang, setLang] = useState(pref('jarvis.lang', 'en-US'))
  const [voice, setVoice] = useState(pref('jarvis.voice', 'natural'))
  const [saving, setSaving] = useState(false)

  useEffect(() => { getSettings().then((s) => { setSettings(s); setProviderState(s.provider); setModel(s.model) }).catch(() => {}) }, [])

  const changeProvider = (p: string) => {
    setProviderState(p); savePref('jarvis.provider', p); setSaving(true)
    apiSetProvider(p, model || undefined).then((s) => { setSettings(s); setModel(s.model) }).catch(() => {}).finally(() => setSaving(false))
  }

  return (
    <>
      <div className="cards3">
        <div className="scard"><div className="lbl">AI PROVIDER</div><div className="big">{(settings?.provider ?? provider) === 'claude' ? 'Anthropic' : 'Mock'}</div><div className="sub">{settings?.hasAnthropicKey ? 'API key set' : 'no key · offline'}</div></div>
        <div className="scard"><div className="lbl">STT ENGINE</div><div className="big">{stt === 'whisper' ? 'Whisper' : 'Local'}</div><div className="sub">{stt === 'whisper' ? 'cloud · accurate' : 'offline'}</div></div>
        <div className="scard"><div className="lbl">TTS ENGINE</div><div className="big">{tts === 'system' ? 'System' : 'ElevenLabs'}</div><div className="sub">{tts === 'system' ? 'native · fastest' : 'cloud'}</div></div>
      </div>

      <div className="field"><label>AI provider {saving && <span className="spin-fast">◠</span>}</label>
        <select value={provider} onChange={(e) => changeProvider(e.target.value)}>
          <option value="mock">Mock (offline · no key)</option>
          <option value="claude">Anthropic Claude (real reasoning)</option>
        </select>
        <div className="note">{provider === 'claude' && !settings?.hasAnthropicKey ? 'No ANTHROPIC_API_KEY set — calls fall back to the offline mock.' : 'Takes effect immediately for new requests.'}</div></div>
      <div className="field"><label>Model</label>
        <input value={model} onChange={(e) => setModel(e.target.value)} onBlur={() => apiSetProvider(provider, model).then(setSettings).catch(() => {})} placeholder="claude-opus-4-8" /></div>

      <div className="field"><label>Speech-to-text engine</label>
        <select value={stt} onChange={(e) => { setStt(e.target.value); savePref('jarvis.stt', e.target.value) }}>
          <option value="whisper">OpenAI Whisper (cloud · higher accuracy)</option><option value="browser">Browser / local (offline)</option></select>
        <div className="note">The live mic uses the browser's recognizer today; Whisper is the upgrade path.</div></div>
      <div className="field"><label>Text-to-speech engine</label>
        <select value={tts} onChange={(e) => { setTts(e.target.value); savePref('jarvis.tts', e.target.value) }}>
          <option value="system">System TTS (native · fastest)</option><option value="eleven">ElevenLabs (cloud)</option></select></div>
      <div className="field"><label>Voice language</label>
        <select value={lang} onChange={(e) => { setLang(e.target.value); savePref('jarvis.lang', e.target.value) }}>
          <option value="en-US">English (US)</option><option value="en-GB">English (UK)</option><option value="he-IL">Hebrew</option></select></div>
      <div className="field"><label>Voice type</label>
        <div className="seg">
          <button className={voice === 'natural' ? 'on' : ''} onClick={() => { setVoice('natural'); savePref('jarvis.voice', 'natural') }}><span className="pip" />Natural</button>
          <button className={voice === 'robotic' ? 'on' : ''} onClick={() => { setVoice('robotic'); savePref('jarvis.voice', 'robotic') }}><span className="pip" />Robotic</button>
        </div></div>
    </>
  )
}

function ResultWindow({ payload }: { payload?: unknown }) {
  const p = payload as { message?: string; data?: unknown } | undefined
  return (<>
    {p?.message && <div className="answer-txt" style={{ marginBottom: 14 }}>{p.message}</div>}
    {p?.data ? <pre className="raw">{typeof p.data === 'string' ? p.data : JSON.stringify(p.data, null, 2)}</pre> : null}
  </>)
}

function ResponseWindow({ payload }: { payload?: unknown }) {
  const p = payload as { loading?: boolean; resp?: ChatResponse } | undefined
  if (!p || p.loading) return <div className="w-empty"><span className="spin-fast">◠</span><div className="s">Jarvis is thinking…</div></div>
  const r = p.resp!
  return (<>
    <div className="w-section-title" style={{ marginBottom: 10 }}>{r.agent} agent</div>
    <div className="answer-txt">{r.answer}</div>
    <div className="answer-meta"><span>model {r.model}</span><span>{r.tokens} tokens</span>{r.steps.length > 0 && <span>{r.steps.length} steps</span>}</div>
  </>)
}

function WindowBody({ win }: { win: Win }) {
  switch (win.kind) {
    case 'today': return <TodayWindow />
    case 'memory': return <MemoryWindow />
    case 'history': return <HistoryWindow />
    case 'agents': return <AgentsWindow />
    case 'logs': return <LogsWindow />
    case 'settings': return <SettingsWindow />
    case 'result': return <ResultWindow payload={win.payload} />
    case 'response': return <ResponseWindow payload={win.payload} />
  }
}

/* ===========================================================================
   Command palette
=========================================================================== */
function CommandPalette({ commands, onRun, onClose }: { commands: CommandDefinition[]; onRun: (s: string) => void; onClose: () => void }) {
  const [q, setQ] = useState(''); const [sel, setSel] = useState(0)
  const filtered = commands.filter((c) => (c.slash + ' ' + c.description).toLowerCase().includes(q.toLowerCase())).slice(0, 40)
  useEffect(() => setSel(0), [q])
  return (
    <div className="cmdk-overlay" onClick={onClose}>
      <div className="cmdk" onClick={(e) => e.stopPropagation()}>
        <input autoFocus placeholder="Run a command…  /today  /memory  /workflows  /policy" value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown') { e.preventDefault(); setSel((s) => Math.min(s + 1, filtered.length - 1)) }
            else if (e.key === 'ArrowUp') { e.preventDefault(); setSel((s) => Math.max(s - 1, 0)) }
            else if (e.key === 'Enter' && filtered[sel]) onRun(filtered[sel].slash)
            else if (e.key === 'Escape') onClose()
          }} />
        <div className="cmdk-list">
          {filtered.map((c, i) => (
            <button key={c.slash} className={`cmdk-item${i === sel ? ' sel' : ''}`} onMouseEnter={() => setSel(i)} onClick={() => onRun(c.slash)}>
              <span className="slash">{c.slash}</span><span className="desc">{c.description}</span></button>
          ))}
          {filtered.length === 0 && <div className="w-empty"><div className="s">No matching command.</div></div>}
        </div>
      </div>
    </div>
  )
}

/* ===========================================================================
   App
=========================================================================== */
const SLASH_WINDOW: Record<string, WinKind> = {
  '/today': 'today', '/memory': 'memory', '/tasks': 'history', '/history': 'history',
  '/agents': 'agents', '/logs': 'logs', '/settings': 'settings',
}
const WIN_META: Record<WinKind, { title: string; subtitle: string; dim: string }> = {
  today: { title: 'Jarvis Today', subtitle: 'Daily digest — counts, highlights', dim: '720×600' },
  memory: { title: 'Memory', subtitle: 'Trusted facts', dim: '720×640' },
  history: { title: 'Multi-step history', subtitle: 'Prompts and their sub-step trees', dim: '900×620' },
  settings: { title: 'Settings', subtitle: 'Voice · models · privacy', dim: '880×640' },
  agents: { title: 'Agents', subtitle: 'The roster', dim: '760×560' },
  logs: { title: 'Activity log', subtitle: 'Recent audited actions', dim: '760×560' },
  result: { title: 'Result', subtitle: '', dim: '720×520' },
  response: { title: 'Jarvis', subtitle: 'Response', dim: '660×440' },
}

export default function App() {
  const [snap, setSnap] = useState<MonitorSnapshot | null>(null)
  const [now, setNow] = useState(new Date())
  const [commands, setCommands] = useState<CommandDefinition[]>([])
  const [unread, setUnread] = useState(0)
  const [settings, setSettings] = useState<SettingsView | null>(null)
  const [busy, setBusy] = useState(false)
  const [input, setInput] = useState('')
  const [cmdkOpen, setCmdkOpen] = useState(false)
  const [wins, setWins] = useState<Win[]>([])
  const [listening, setListening] = useState(false)
  const [wake, setWake] = useState(false)
  const zRef = useRef(30)
  const wakeRef = useRef<any>(null)
  const ttsOn = pref('jarvis.tts', 'system') !== 'off'

  useEffect(() => {
    fetchCommands().then(setCommands).catch(() => {})
    getSettings().then(setSettings).catch(() => {})
    const poll = () => { getStatus().then(setSnap).catch(() => {}); getUnreadCount().then(setUnread).catch(() => {}) }
    poll()
    const s = setInterval(poll, 3000)
    const c = setInterval(() => setNow(new Date()), 1000)
    const k = (e: KeyboardEvent) => { if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setCmdkOpen((o) => !o) } }
    window.addEventListener('keydown', k)
    return () => { clearInterval(s); clearInterval(c); window.removeEventListener('keydown', k) }
  }, [])

  const focusWin = useCallback((key: string) => setWins((ws) => ws.map((w) => w.key === key ? { ...w, z: ++zRef.current } : w)), [])
  const closeWin = useCallback((key: string) => setWins((ws) => ws.filter((w) => w.key !== key)), [])

  const openWindow = useCallback((kind: WinKind, payload?: unknown, titleOverride?: string) => {
    const meta = WIN_META[kind]
    const singleton = kind !== 'result' && kind !== 'response'
    setWins((ws) => {
      if (singleton) {
        const existing = ws.find((w) => w.kind === kind)
        if (existing) return ws.map((w) => w.key === existing.key ? { ...w, z: ++zRef.current } : w)
      }
      const n = ws.length
      const key = singleton ? kind : `${kind}-${zRef.current}`
      const win: Win = {
        key, kind, title: titleOverride ?? meta.title, subtitle: meta.subtitle, dim: meta.dim,
        x: 320 + (n % 4) * 36, y: 150 + (n % 4) * 30, z: ++zRef.current, payload,
      }
      return [...ws, win]
    })
    return singleton ? kind : `${kind}-${zRef.current}`
  }, [])

  const patchWin = useCallback((key: string, payload: unknown) => setWins((ws) => ws.map((w) => w.key === key ? { ...w, payload } : w)), [])

  const askChat = useCallback(async (message: string, spoken = false) => {
    setBusy(true)
    const key = `response-${++zRef.current}`
    setWins((ws) => [...ws, { key, kind: 'response', title: 'Jarvis', subtitle: 'Response', dim: '660×440', x: 360, y: 200, z: zRef.current, payload: { loading: true } }])
    try {
      const resp = await chat(message)
      patchWin(key, { resp })
      if (spoken && ttsOn) speak(resp.answer)
    } catch (e) {
      patchWin(key, { resp: { answer: `Error: ${(e as Error).message}`, agent: 'system', steps: [], taskId: '', tokens: 0, model: '-' } })
    } finally { setBusy(false) }
  }, [patchWin, ttsOn])

  const runCmd = useCallback(async (slash: string) => {
    setCmdkOpen(false)
    const win = SLASH_WINDOW[slash.split(' ')[0]]
    if (win) { openWindow(win); return }
    try {
      const res = await runCommand(slash)
      openWindow('result', { message: res.message, data: res.data }, slash.split(' ')[0])
    } catch (e) {
      openWindow('result', { message: (e as Error).message }, slash)
    }
  }, [openWindow])

  const submit = useCallback((text: string, spoken = false) => {
    const t = text.trim(); if (!t) return
    setInput('')
    if (t.startsWith('/')) runCmd(t)
    else askChat(t, spoken)
  }, [runCmd, askChat])

  // --- Voice -------------------------------------------------------------
  const startPTT = useCallback(() => {
    if (!SR) { alert('Voice input needs a Chromium-based browser (Web Speech API).'); return }
    const r = new SR(); r.lang = pref('jarvis.lang', 'en-US'); r.interimResults = false; r.maxAlternatives = 1
    r.onresult = (e: any) => { const t = e.results[0][0].transcript as string; submit(t, true) }
    r.onend = () => setListening(false)
    r.onerror = () => setListening(false)
    try { r.start(); setListening(true) } catch { setListening(false) }
  }, [submit])

  const toggleWake = useCallback(() => {
    if (wakeRef.current) { wakeRef.current.stop?.(); wakeRef.current = null; setWake(false); return }
    if (!SR) { alert('Wake-word needs a Chromium-based browser (Web Speech API).'); return }
    const r = new SR(); r.lang = pref('jarvis.lang', 'en-US'); r.continuous = true; r.interimResults = false
    r.onresult = (e: any) => {
      const t = (e.results[e.results.length - 1][0].transcript as string).toLowerCase()
      const i = t.indexOf('jarvis')
      if (i >= 0) { const cmd = t.slice(i + 6).trim(); if (cmd.length > 1) submit(cmd, true) }
    }
    r.onend = () => { if (wakeRef.current) { try { r.start() } catch { /* restart race */ } } }
    r.onerror = () => {}
    try { r.start(); wakeRef.current = r; setWake(true) } catch { setWake(false) }
  }, [submit])

  const uiProvider = settings ? (settings.provider === 'claude' || settings.provider === 'anthropic' ? 'anthropic' : settings.provider) : 'mock'
  const pickProvider = useCallback((ui: string) => {
    const backend = ui === 'anthropic' ? 'claude' : ui
    apiSetProvider(backend).then(setSettings).catch(() => {})
  }, [])

  const cpu = pct(snap?.cpu.systemCpuLoad)
  const memPctV = snap ? Math.round((snap.memory.usedPhysicalBytes / snap.memory.totalPhysicalBytes) * 100) : 0
  const caption = busy ? 'Processing' : 'Online · Standby'

  return (
    <>
      {/* centerpiece */}
      <div className="stage"><Sphere kind="gyro" busy={busy} caption={caption} /></div>

      {/* HUD corners */}
      <div className="hud">
        <div className="hud-tl">
          <div className="wordmark"><span className="pip" /><b>J.A.R.V.I.S</b></div>
          <div className="tagline">JUST A RATHER VERY INTELLIGENT SYSTEM</div>
          <div className="tele">
            <span className="k">STATUS</span><span className="v good">{snap?.jarvisHealth === 'OK' ? 'OPERATIONAL' : 'BOOTING'}</span>
            <span className="k">CPU</span><span className="v">{cpu}%</span>
            <span className="k">MEMORY</span><span className="v">{snap ? `${gb(snap.memory.usedPhysicalBytes)}/${gb(snap.memory.totalPhysicalBytes)}G (${memPctV}%)` : '—'}</span>
            <span className="k">LOAD</span><span className="v">{snap?.cpu.systemLoadAverage?.toFixed(2) ?? '—'}</span>
            <span className="k">DISK</span><span className="v">{snap ? `${gb(snap.disk.freeBytes)}G free` : '—'}</span>
            <span className="k">AGENTS</span><span className="v">{snap?.runtime.registeredAgents ?? '—'}</span>
            <span className="k">NETWORK</span><span className="v good">LAN</span>
          </div>
        </div>

        <div className="hud-tr">
          <div className="clock">{now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</div>
          <div className="datestr">{now.toLocaleDateString([], { weekday: 'short', month: 'short', day: '2-digit', year: 'numeric' }).toUpperCase()}</div>
          <div className="row2">
            <button className="iconbtn" title="Refresh" onClick={() => getStatus().then(setSnap)}>⟳</button>
            <button className="iconbtn" title="Notifications" onClick={async () => { const items = await getNotifications().catch(() => [] as NotificationItem[]); markNotificationsRead().then(() => setUnread(0)).catch(() => {}); openWindow('result', { message: items.length ? '' : 'Nothing yet.', data: items.length ? items.map((n) => `• ${n.title}${n.body ? ' — ' + n.body : ''}`).join('\n') : null }, 'Notifications') }}>
              🔔{unread > 0 && <span className="count">{unread}</span>}</button>
          </div>
          <div className="telemetry-flag"><span className="pip" />TELEMETRY ACTIVE</div>
        </div>

        <div className="hud-bl">
          <button className="userchip">
            <span className="av">S</span>
            <span><span className="nm">Shay ⌄</span><div className="sub">CPU {cpu}% · MEM {gb(snap?.memory.usedPhysicalBytes)}/{gb(snap?.memory.totalPhysicalBytes)}G</div></span>
          </button>
        </div>

        <div className="hud-br">
          <div className="providers">
            {(['ollama', 'openai', 'anthropic'] as const).map((p) => (
              <button key={p} className={provider === p ? 'on' : ''} onClick={() => { setProvider(p); openWindow('settings') }}><span className="pip" />{p.toUpperCase()}</button>
            ))}
          </div>
        </div>
      </div>

      {/* bottom command dock */}
      <div className="dock">
        <div className="bar">
          <input placeholder="Ask Jarvis, or type a /command…" value={input}
            onChange={(e) => setInput(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') submit(input) }} />
          <button className="go" onClick={() => submit(input)}>{busy ? <span className="spin-fast">◠</span> : '➤'}</button>
        </div>
        <div className="hintrow">
          <button className="hint" onClick={() => openWindow('today')}>Today</button>
          <button className="hint" onClick={() => openWindow('history')}>History</button>
          <button className="hint" onClick={() => openWindow('memory')}>Memory</button>
          <button className="hint" onClick={() => openWindow('agents')}>Agents</button>
          <button className="hint" onClick={() => setCmdkOpen(true)}>⌘K</button>
        </div>
      </div>

      {/* floating windows */}
      <div className="winlayer">
        {wins.map((w) => (
          <FloatingWindow key={w.key} win={w} onClose={() => closeWin(w.key)} onFocus={() => focusWin(w.key)}>
            <WindowBody win={w} />
          </FloatingWindow>
        ))}
      </div>

      {cmdkOpen && <CommandPalette commands={commands} onRun={runCmd} onClose={() => setCmdkOpen(false)} />}
    </>
  )
}
