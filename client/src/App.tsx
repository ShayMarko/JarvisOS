import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  createBackup, createMemory, deleteMemory, fetchCommands, getAgents, getAudit, getFileContent,
  getMemoryList, getNotifications, getRuns, getSettings, getStatus, getUnreadCount, listBackups,
  listFiles, markNotificationRead, restoreBackup, runCommand, setProvider as apiSetProvider,
  streamInput, writeFile,
} from './api'
import type {
  AgentDef, AuditEntry, BackupInfo, ChatResponse, CommandDefinition, CommandResult, FileNode, Memory,
  MonitorSnapshot, NotificationItem, RunRecord, SettingsView, Step,
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
type WinKind = 'today' | 'memory' | 'history' | 'settings' | 'agents' | 'logs' | 'files' | 'backups' | 'notifications' | 'result' | 'response'
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
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const load = useCallback(() => getMemoryList().then(setItems).catch(() => setItems([])), [])
  useEffect(() => { load() }, [load])
  const add = () => {
    if (!content.trim()) return
    createMemory({ category: 'fact', title: title.trim() || 'Note', content: content.trim(), source: 'manual' })
      .then(() => { setTitle(''); setContent(''); load() }).catch(() => {})
  }
  const del = (id: string) => deleteMemory(id).then(load).catch(() => {})
  return (
    <>
      <div className="w-head-row"><span className="w-section-title">Trusted memory · {items?.length ?? 0}</span>
        <button className="btn-soft" onClick={load}>⟳ Refresh</button></div>
      <div className="mem-add">
        <input placeholder="Title (optional)" value={title} onChange={(e) => setTitle(e.target.value)} style={{ flex: '0 0 150px' }} />
        <input placeholder="Tell Jarvis a fact to remember…" value={content} onChange={(e) => setContent(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') add() }} style={{ flex: 1 }} />
        <button className="btn-soft" onClick={add} disabled={!content.trim()}>+ Remember</button>
      </div>
      {!items ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : items.length === 0 ? <div className="w-empty"><div className="big">◈</div><div className="s">No memory yet. Add a fact above, or just tell Jarvis “remember that …” in the command bar.</div></div>
        : <div className="rows">{items.map((m) => (
            <div className="row" key={m.id}>
              <span className="grow"><strong>{m.title}</strong> — <span style={{ color: 'var(--muted)' }}>{m.content}</span></span>
              <span className="pill low">{m.category}</span>
              <button className="row-del" title="Forget" onClick={() => del(m.id)}>✕</button>
            </div>
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

function NotificationsWindow() {
  const [items, setItems] = useState<NotificationItem[] | null>(null)
  useEffect(() => { getNotifications().then(setItems).catch(() => setItems([])) }, [])
  return !items ? <div className="w-empty"><span className="spin-fast">◠</span></div>
    : items.length === 0 ? <div className="w-empty"><div className="big">🔔</div><div className="s">Nothing yet — you’re all caught up.</div></div>
    : <div className="rows">{items.map((n) => (
        <div className="row" key={n.id} style={{ alignItems: 'flex-start' }}>
          <span className={`dot-s ${n.type === 'error' ? 'bad' : n.type === 'warning' ? 'warn' : 'ok'}`} style={{ marginTop: 5 }} />
          <span className="grow" style={{ whiteSpace: 'normal' }}><strong>{n.title}</strong>{n.body ? <div style={{ color: 'var(--muted)', fontSize: 12 }}>{n.body}</div> : null}</span>
          <span className="when">{ago(n.createdAt)}</span>
        </div>))}</div>
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

function FilesWindow() {
  const [cwd, setCwd] = useState('')
  const [entries, setEntries] = useState<FileNode[] | null>(null)
  const [sel, setSel] = useState<FileNode | null>(null)
  const [content, setContent] = useState('')
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')

  const load = useCallback((path: string) => {
    setEntries(null); setSel(null); setMsg('')
    listFiles(path).then(setEntries).catch((e) => { setEntries([]); setMsg(friendlyError((e as Error).message)) })
  }, [])
  useEffect(() => { load(cwd) }, [cwd, load])

  const open = (n: FileNode) => {
    if (n.directory) { setCwd(n.path); return }
    setBusy(true); setMsg('')
    getFileContent(n.path)
      .then((f) => { setSel(n); setContent(f.content); setDirty(false) })
      .catch((e) => setMsg(friendlyError((e as Error).message)))
      .finally(() => setBusy(false))
  }
  const save = () => {
    if (!sel) return
    setBusy(true)
    writeFile(sel.path, content)
      .then(() => { setDirty(false); setMsg('Saved.'); setTimeout(() => setMsg(''), 1800) })
      .catch((e) => setMsg(friendlyError((e as Error).message)))
      .finally(() => setBusy(false))
  }
  const crumbs = cwd ? cwd.split('/').filter(Boolean) : []

  return (
    <div className="files">
      <div className="files-bar">
        <button className="hint" disabled={!cwd} onClick={() => setCwd(crumbs.slice(0, -1).join('/'))}>↑ Up</button>
        <span className="crumbs"><button className="crumb" onClick={() => setCwd('')}>Explorer</button>
          {crumbs.map((c, i) => <button key={i} className="crumb" onClick={() => setCwd(crumbs.slice(0, i + 1).join('/'))}>/ {c}</button>)}</span>
        <span className="grow" />
        {msg && <span className="files-msg">{msg}</span>}
      </div>
      <div className="files-split">
        <div className="files-list">
          {!entries ? <div className="w-empty"><span className="spin-fast">◠</span></div>
            : entries.length === 0 ? <div className="w-empty"><div className="s">Empty folder.</div></div>
            : entries.map((n) => (
              <button key={n.path} className={`file-row${sel?.path === n.path ? ' sel' : ''}`} onClick={() => open(n)}>
                <span className="fic">{n.directory ? '📁' : '📄'}</span>
                <span className="fname">{n.name}</span>
                <span className="fsize">{n.directory ? '' : kb(n.size)}</span>
              </button>))}
        </div>
        <div className="files-view">
          {!sel ? <div className="w-empty"><div className="s">Select a file to view or edit it.</div></div>
            : <>
              <div className="files-vhead"><strong>{sel.name}</strong>
                <span className="grow" />
                <button className="hint" disabled={busy || !dirty} onClick={save}>{busy ? '…' : dirty ? 'Save' : 'Saved'}</button></div>
              <textarea className="files-edit" value={content} spellCheck={false}
                onChange={(e) => { setContent(e.target.value); setDirty(true) }} />
            </>}
        </div>
      </div>
    </div>
  )
}

function BackupsWindow() {
  const [items, setItems] = useState<BackupInfo[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const refresh = useCallback(() => { listBackups().then(setItems).catch(() => setItems([])) }, [])
  useEffect(() => { refresh() }, [refresh])

  const make = () => {
    setBusy(true); setMsg('')
    createBackup().then(() => { setMsg('Snapshot created.'); refresh() })
      .catch((e) => setMsg(friendlyError((e as Error).message))).finally(() => setBusy(false))
  }
  const restore = (name: string) => {
    if (!confirm(`Restore "${name}"? This overwrites current files in the Explorer with the snapshot's versions.`)) return
    setBusy(true); setMsg('')
    restoreBackup(name).then((r) => setMsg(r.message))
      .catch((e) => setMsg(friendlyError((e as Error).message))).finally(() => setBusy(false))
  }

  return (
    <>
      <div className="files-bar">
        <button className="hint" disabled={busy} onClick={make}>{busy ? '…' : '+ New snapshot'}</button>
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        {msg && <span className="files-msg">{msg}</span>}
      </div>
      {!items ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : items.length === 0 ? <div className="w-empty"><div className="big">🗄</div><div className="s">No snapshots yet. Create one to protect your Explorer.</div></div>
        : <div className="rows">{items.map((b) => (
            <div className="row" key={b.name}>
              <span className="grow"><strong>{b.name}</strong> <span style={{ color: 'var(--muted)' }}>· {kb(b.sizeBytes)}</span>
                <div style={{ color: 'var(--muted)', fontSize: 12 }}>{new Date(b.createdAt).toLocaleString()}</div></span>
              <button className="hint" disabled={busy} onClick={() => restore(b.name)}>Restore</button>
            </div>))}</div>}
    </>
  )
}

/** Format a byte count compactly (B / KB / MB). */
function kb(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** Format a per-second throughput for the telemetry readout. */
function rate(bytesPerSec?: number): string {
  if (!bytesPerSec || bytesPerSec < 1) return '0'
  if (bytesPerSec < 1024) return `${Math.round(bytesPerSec)} B/s`
  if (bytesPerSec < 1024 * 1024) return `${(bytesPerSec / 1024).toFixed(0)} KB/s`
  return `${(bytesPerSec / 1024 / 1024).toFixed(1)} MB/s`
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

/** True when a string is actually an HTML page / markup that leaked through. */
function isHtmlish(s?: string): boolean {
  if (!s) return false
  return /<!doctype|<html|<\/?[a-z]+[^>]*>/i.test(s) && (s.match(/<[^>]+>/g)?.length ?? 0) > 2
}
/** Strip tags/entities and collapse whitespace — used to tidy error text for humans. */
function stripHtml(s: string): string {
  const t = s.replace(/<[^>]*>/g, ' ').replace(/&[a-z#0-9]+;/gi, ' ').replace(/\s+/g, ' ').trim()
  return t.length > 200 ? t.slice(0, 200) + '…' : t
}
/** A friendly, human message for an error string (no codes, no HTML, no jargon). */
function friendlyError(raw?: string): string {
  if (!raw) return 'Something went wrong. Please try again.'
  if (isHtmlish(raw)) return 'The service returned an unexpected response. Please try again in a moment.'
  const r = raw.replace(/^Error:\s*/i, '').trim()
  if (/403|forbidden|declined/i.test(r)) return 'A service declined the request — it may be rate-limiting us. Please try again shortly.'
  if (/timeout|timed out/i.test(r)) return 'That took too long and timed out. Please try again.'
  if (/connect|refused|unreachable|unavailable/i.test(r)) return "I couldn't reach that service. It may be offline — please try again."
  if (/permission|denied|blocked/i.test(r)) return r // policy/permission messages are already user-facing
  return r.length > 200 ? r.slice(0, 200) + '…' : r
}

/** A friendly error card for non-technical users. */
function ErrorCard({ message }: { message?: string }) {
  return (
    <div className="err-card">
      <span className="ic">⚠</span>
      <div><div className="t">Something went wrong</div><div className="m">{friendlyError(message)}</div></div>
    </div>
  )
}

/** Humanize a JSON key: camelCase / snake-case / kebab → "Title Case". */
function humanize(key: string): string {
  return key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

/** Recursively renders any value (object / array / scalar) as a clean HUD view. */
function DataView({ value }: { value: unknown }): React.ReactElement {
  if (value === null || value === undefined || value === '') return <span className="dv-empty">—</span>
  if (typeof value === 'string') return value.includes('\n') ? <pre className="raw">{value}</pre> : <span className="dv-scalar">{value}</span>
  if (typeof value === 'number') return <span className="dv-num">{value.toLocaleString()}</span>
  if (typeof value === 'boolean') return <span className={`dv-bool ${value ? 't' : 'f'}`}>{value ? 'yes' : 'no'}</span>
  if (Array.isArray(value)) {
    if (value.length === 0) return <span className="dv-empty">none</span>
    const allScalar = value.every((v) => v === null || typeof v !== 'object')
    if (allScalar) return <div className="dv-list">{value.map((v, i) => <div className="dv-li" key={i}>{String(v)}</div>)}</div>
    return <div>{value.map((v, i) => <div className="dv-card" key={i}><DataView value={v} /></div>)}</div>
  }
  const entries = Object.entries(value as Record<string, unknown>)
  if (entries.length === 0) return <span className="dv-empty">empty</span>
  return (
    <div className="dv">
      {entries.map(([k, v]) => {
        const nested = v !== null && typeof v === 'object' && (Array.isArray(v) ? v.some((x) => x && typeof x === 'object') : true)
        if (nested) return (
          <div className="dv-section" key={k}>
            <div className="dv-sec-title">{humanize(k)}</div>
            <div className="dv-sec-body"><DataView value={v} /></div>
          </div>
        )
        return <div className="dv-row" key={k}><span className="dv-key">{humanize(k)}</span><span className="dv-val"><DataView value={v} /></span></div>
      })}
    </div>
  )
}

function ResultWindow({ payload }: { payload?: unknown }) {
  const p = payload as { status?: string; message?: string; data?: unknown } | undefined
  if (p?.status === 'ERROR') return <ErrorCard message={p.message} />
  const hasData = p?.data !== undefined && p?.data !== null && p?.data !== ''
  return (<>
    {p?.message && <div className="answer-txt" style={{ marginBottom: hasData ? 16 : 0 }}>{isHtmlish(p.message) ? stripHtml(p.message) : p.message}</div>}
    {hasData ? <DataView value={p!.data} /> : (!p?.message ? <div className="w-empty"><div className="s">Done.</div></div> : null)}
  </>)
}

function ResponseWindow({ payload }: { payload?: unknown }) {
  const p = payload as { loading?: boolean; steps?: Step[]; resp?: ChatResponse; commandResult?: { status?: string; message?: string; data?: unknown } } | undefined
  const steps = p?.steps ?? []
  const r = p?.resp
  const cr = p?.commandResult
  const hasCrData = cr?.data !== undefined && cr?.data !== null && cr?.data !== ''
  return (
    <>
      {steps.length > 0 && (
        <div className="substeps" style={{ marginLeft: 0, paddingLeft: 0, marginBottom: 12 }}>
          {steps.map((s, i) => (
            <div className="substep" key={i}><span className="kind">{s.kind}</span><span className="lbl">{s.label}{s.detail ? <span className="det"> — {s.detail}</span> : null}</span></div>
          ))}
          {p?.loading && <div className="substep"><span className="kind"><span className="spin-fast">◠</span></span><span className="det">working…</span></div>}
        </div>
      )}
      {!r && !cr && p?.loading && steps.length === 0 && <div className="w-empty"><span className="spin-fast">◠</span><div className="s">Jarvis is thinking…</div></div>}
      {r && (
        <>
          <div className="w-section-title" style={{ margin: '4px 0 8px' }}>{r.agent} agent</div>
          {isHtmlish(r.answer) ? <ErrorCard message={r.answer} /> : <div className="answer-txt">{r.answer}</div>}
          <div className="answer-meta"><span>model {r.model}</span><span>{r.tokens} tokens</span>{r.steps.length > 0 && <span>{r.steps.length} steps</span>}</div>
        </>
      )}
      {!r && cr && (cr.status === 'ERROR'
        ? <ErrorCard message={cr.message} />
        : <>
            {cr.message && <div className="answer-txt" style={{ marginBottom: hasCrData ? 14 : 0 }}>{isHtmlish(cr.message) ? stripHtml(cr.message) : cr.message}</div>}
            {hasCrData ? <DataView value={cr.data} /> : null}
          </>)}
    </>
  )
}

function WindowBody({ win }: { win: Win }) {
  switch (win.kind) {
    case 'today': return <TodayWindow />
    case 'memory': return <MemoryWindow />
    case 'history': return <HistoryWindow />
    case 'agents': return <AgentsWindow />
    case 'logs': return <LogsWindow />
    case 'files': return <FilesWindow />
    case 'backups': return <BackupsWindow />
    case 'settings': return <SettingsWindow />
    case 'notifications': return <NotificationsWindow />
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
  '/files': 'files', '/jfiles': 'files', '/backup': 'backups', '/backups': 'backups',
}
const WIN_META: Record<WinKind, { title: string; subtitle: string; dim: string }> = {
  today: { title: 'Jarvis Today', subtitle: 'Daily digest — counts, highlights', dim: '720×600' },
  memory: { title: 'Memory', subtitle: 'Trusted facts', dim: '720×640' },
  history: { title: 'Multi-step history', subtitle: 'Prompts and their sub-step trees', dim: '900×620' },
  settings: { title: 'Settings', subtitle: 'Voice · models · privacy', dim: '880×640' },
  agents: { title: 'Agents', subtitle: 'The roster', dim: '760×560' },
  logs: { title: 'Activity log', subtitle: 'Recent audited actions', dim: '760×560' },
  files: { title: 'Explorer', subtitle: 'Browse · view · edit files', dim: '880×620' },
  backups: { title: 'Backups', subtitle: 'Snapshot · restore the Explorer', dim: '720×560' },
  notifications: { title: 'Notifications', subtitle: 'Recent alerts', dim: '520×520' },
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
  const [notifOpen, setNotifOpen] = useState(false)
  const [notifItems, setNotifItems] = useState<NotificationItem[]>([])
  const [notifExpanded, setNotifExpanded] = useState<string | null>(null)
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

  // Single cognitive door: raw input → backend InputRouter → command or streamed Brain.
  const askInput = useCallback((raw: string, spoken = false) => {
    setBusy(true)
    const key = `response-${++zRef.current}`
    setWins((ws) => [...ws, { key, kind: 'response', title: 'Jarvis', subtitle: raw, dim: '660×460', x: 360, y: 190, z: zRef.current, payload: { loading: true, steps: [] } }])
    const collected: Step[] = []
    streamInput(raw, {
      onStep: (s) => { collected.push(s); patchWin(key, { loading: true, steps: [...collected] }) },
      onResult: (result: CommandResult) => {
        if (result.type === 'chat') {
          const resp = result.data as ChatResponse
          patchWin(key, { loading: false, steps: [...collected], resp })
          if (spoken && ttsOn) speak(resp.answer)
        } else {
          patchWin(key, { loading: false, steps: [...collected], commandResult: { status: result.status, message: result.message, data: result.data } })
          if (spoken && ttsOn && result.status !== 'ERROR' && result.message) speak(result.message)
        }
      },
      onError: (m) => patchWin(key, { loading: false, steps: [...collected], commandResult: { status: 'ERROR', message: m } }),
      onDone: () => setBusy(false),
    })
  }, [patchWin, ttsOn])

  const runCmd = useCallback(async (slash: string) => {
    setCmdkOpen(false)
    const win = SLASH_WINDOW[slash.split(' ')[0]]
    if (win) { openWindow(win); return }
    try {
      const res = await runCommand(slash)
      openWindow('result', { status: res.status, message: res.message, data: res.data }, slash.split(' ')[0])
    } catch (e) {
      openWindow('result', { status: 'ERROR', message: (e as Error).message }, slash)
    }
  }, [openWindow])

  const submit = useCallback((text: string, spoken = false) => {
    const t = text.trim(); if (!t) return
    setInput('')
    // Window-opener slashes are pure UI nav → open instantly, no round trip.
    if (t.startsWith('/') && SLASH_WINDOW[t.split(' ')[0]]) { openWindow(SLASH_WINDOW[t.split(' ')[0]]); return }
    // Everything else (free text + non-window commands) goes through the one cognitive door.
    askInput(t, spoken)
  }, [askInput, openWindow])

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

  // Expand a notification to read it fully; mark just that one read on first open.
  const toggleNotif = useCallback((n: NotificationItem) => {
    setNotifExpanded((id) => (id === n.id ? null : n.id))
    if (!n.read) {
      markNotificationRead(n.id).catch(() => {})
      setNotifItems((items) => items.map((x) => (x.id === n.id ? { ...x, read: true } : x)))
      setUnread((u) => Math.max(0, u - 1))
    }
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
            <span className="k">NETWORK</span><span className="v">{snap?.network ? `↓${rate(snap.network.rxBytesPerSec)} ↑${rate(snap.network.txBytesPerSec)}` : 'LAN'}</span>
          </div>
        </div>

        <div className="hud-tr">
          <div className="clock">{now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</div>
          <div className="datestr">{now.toLocaleDateString([], { weekday: 'short', month: 'short', day: '2-digit', year: 'numeric' }).toUpperCase()}</div>
          <div className="row2">
            <button className="iconbtn" title="Refresh" onClick={() => getStatus().then(setSnap)}>⟳</button>
            <button className="iconbtn" title="Notifications" onClick={() => setNotifOpen((o) => {
              const next = !o
              if (next) { setNotifExpanded(null); getNotifications().then(setNotifItems).catch(() => {}) }
              return next
            })}>
              🔔{unread > 0 && <span className="count">{unread}</span>}</button>
          </div>
          <div className="telemetry-flag"><span className="pip" />TELEMETRY ACTIVE</div>
        </div>

        <div className="hud-bl">
          <button className="userchip" title="Open settings" onClick={() => openWindow('settings')}>
            <span className="av">S</span>
            <span className="nm">Shay <span className="caret">⌄</span></span>
          </button>
        </div>

        <div className="hud-br">
          <div className="providers">
            {(['ollama', 'openai', 'anthropic'] as const).map((p) => (
              <button key={p} className={uiProvider === p ? 'on' : ''} onClick={() => pickProvider(p)} title="Switch AI provider"><span className="pip" />{p.toUpperCase()}</button>
            ))}
          </div>
        </div>
      </div>

      {/* bottom command dock */}
      <div className="dock">
        <div className="bar">
          <button className={`mic${listening ? ' live' : ''}`} title="Push to talk" onClick={startPTT}>{listening ? '◉' : '🎙'}</button>
          <input placeholder={listening ? 'Listening…' : 'Ask Jarvis, or type a /command…'} value={input}
            onChange={(e) => setInput(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') submit(input) }} />
          <button className="go" onClick={() => submit(input)}>{busy ? <span className="spin-fast">◠</span> : '➤'}</button>
        </div>
        <div className="hintrow">
          <button className={`wake${wake ? ' on' : ''}`} onClick={toggleWake} title='Say "Jarvis …" to command hands-free'><span className="sw" />WAKE WORD</button>
          <button className="hint" onClick={() => openWindow('today')}>Today</button>
          <button className="hint" onClick={() => openWindow('history')}>History</button>
          <button className="hint" onClick={() => openWindow('memory')}>Memory</button>
          <button className="hint" onClick={() => openWindow('files')}>Files</button>
          <button className="hint" onClick={() => openWindow('backups')}>Backups</button>
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

      {/* notifications popover (anchored under the bell) */}
      {notifOpen && (
        <>
          <div className="notif-overlay" onClick={() => setNotifOpen(false)} />
          <div className="notif-pop">
            <div className="head"><span className="t">Notifications</span>{notifItems.length > 0 && <span className="mono" style={{ fontSize: 10, color: 'var(--muted)' }}>{notifItems.length}</span>}</div>
            <div className="list">
              {notifItems.length === 0
                ? <div className="empty">Nothing yet — you’re all caught up.</div>
                : notifItems.slice(0, 8).map((n) => {
                    const open = notifExpanded === n.id
                    return (
                      <div key={n.id}>
                        <button className={`item ${n.read ? 'read' : 'unread'}`} onClick={() => toggleNotif(n)}>
                          <span className={`dot-s ${n.type === 'error' ? 'bad' : n.type === 'warning' ? 'warn' : 'ok'}`} style={{ marginTop: 5 }} />
                          <span className="body"><div className="ttl">{n.title}</div>{!open && n.body && <div className="sub">{n.body}</div>}</span>
                          <span className="when">{ago(n.createdAt)}</span>
                        </button>
                        {open && (
                          <div className="detail">
                            {n.body && <div>{n.body}</div>}
                            <div className="meta">
                              <span>{n.type}</span>
                              {n.source && <span>via {n.source}</span>}
                              <span>{new Date(n.createdAt).toLocaleString()}</span>
                            </div>
                          </div>
                        )}
                      </div>
                    )
                  })}
            </div>
            <div className="foot"><button className="showall" onClick={() => { setNotifOpen(false); openWindow('notifications') }}>SHOW ALL →</button></div>
          </div>
        </>
      )}

      {cmdkOpen && <CommandPalette commands={commands} onRun={runCmd} onClose={() => setCmdkOpen(false)} />}
    </>
  )
}
