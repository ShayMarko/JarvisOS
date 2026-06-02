import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  createBackup, createMemory, deleteMemory, fetchCommands, getAgents, getAudit, getConversation,
  getFileContent, getInstalledPlugins, getMemoryList, getNotifications, getPluginCatalog, getRuns,
  getSettings, getStatus, getTokenDashboard, getUnreadCount, installPlugin, listBackups, listFiles,
  markNotificationRead, restoreBackup, revealFile, runCommand, setBudget as apiSetBudget,
  setProvider as apiSetProvider, streamInput, uninstallPlugin, writeFile,
} from './api'
import type {
  AgentDef, AuditEntry, BackupInfo, ChatResponse, CommandDefinition, CommandResult, FileNode, Memory,
  MonitorSnapshot, NotificationItem, PluginCatalogEntry, PluginInfo, RunRecord, SettingsView, Step, TokenDashboard,
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
type WinKind = 'conversation' | 'today' | 'memory' | 'history' | 'settings' | 'agents' | 'logs' | 'files' | 'backups' | 'plugins' | 'tokens' | 'notifications' | 'result' | 'response'

/** One exchange in the running conversation transcript. */
interface Turn {
  id: string
  prompt: string
  loading: boolean
  steps: Step[]
  resp?: ChatResponse
  commandResult?: { status?: string; message?: string; data?: unknown }
}
interface Win { key: string; kind: WinKind; title: string; subtitle: string; dim: string; x: number; y: number; z: number; payload?: unknown }

function FloatingWindow({ win, onClose, onFocus, children }: { win: Win; onClose: () => void; onFocus: () => void; children: React.ReactNode }) {
  const [pos, setPos] = useState({ x: win.x, y: win.y })
  const [w0, h0] = win.dim.split('×').map(Number)
  const [size, setSize] = useState({ w: w0 || 720, h: h0 || 520 })
  const drag = useRef<{ dx: number; dy: number } | null>(null)
  const rez = useRef<{ sx: number; sy: number; w: number; h: number } | null>(null)

  const onDown = (e: React.PointerEvent) => {
    onFocus()
    drag.current = { dx: e.clientX - pos.x, dy: e.clientY - pos.y }
    const move = (ev: PointerEvent) => { if (drag.current) setPos({ x: ev.clientX - drag.current.dx, y: ev.clientY - drag.current.dy }) }
    const up = () => { drag.current = null; window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up) }
    window.addEventListener('pointermove', move); window.addEventListener('pointerup', up)
  }
  // Drag the bottom-right corner to resize (clamped to sane bounds).
  const onResize = (e: React.PointerEvent) => {
    e.stopPropagation(); onFocus()
    rez.current = { sx: e.clientX, sy: e.clientY, w: size.w, h: size.h }
    const move = (ev: PointerEvent) => {
      if (!rez.current) return
      setSize({
        w: Math.max(360, Math.min(window.innerWidth - 40, rez.current.w + (ev.clientX - rez.current.sx))),
        h: Math.max(220, Math.min(window.innerHeight - 40, rez.current.h + (ev.clientY - rez.current.sy))),
      })
    }
    const up = () => { rez.current = null; window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up) }
    window.addEventListener('pointermove', move); window.addEventListener('pointerup', up)
  }

  return (
    <div className="window fade-in" style={{ left: pos.x, top: pos.y, zIndex: win.z, width: size.w, height: size.h }} onMouseDown={onFocus}>
      <div className="win-head" onPointerDown={onDown}>
        <span className="pip" />
        <span className="title">{win.title}</span>
        <span className="subtitle">{win.subtitle}</span>
        <span className="dim">{Math.round(size.w)}×{Math.round(size.h)}</span>
        <button className="close" onClick={onClose}>✕</button>
      </div>
      <div className="win-body">{children}</div>
      <div className="win-resize" onPointerDown={onResize} title="Drag to resize" />
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

const IMG_EXT = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg', 'ico']
const VID_EXT = ['mp4', 'webm', 'm4v', 'ogv', 'mov']
const AUD_EXT = ['mp3', 'wav', 'm4a', 'aac', 'flac', 'ogg', 'oga']
const TEXT_EXT = ['txt', 'md', 'markdown', 'json', 'jsonl', 'xml', 'yaml', 'yml', 'csv', 'tsv', 'log',
  'js', 'jsx', 'ts', 'tsx', 'java', 'kt', 'py', 'rb', 'go', 'rs', 'c', 'h', 'cpp', 'hpp', 'cs', 'php',
  'swift', 'sh', 'bash', 'zsh', 'sql', 'html', 'htm', 'css', 'scss', 'less', 'toml', 'ini', 'cfg',
  'conf', 'env', 'properties', 'gradle', 'dockerfile', 'gitignore', 'editorconfig', 'svg']
function extOf(name: string): string { return name.includes('.') ? name.split('.').pop()!.toLowerCase() : '' }
function rawUrl(path: string): string { return `/api/files/raw?path=${encodeURIComponent(path)}` }
/** How to render a file in the viewer. */
function fileKind(name: string): 'image' | 'video' | 'audio' | 'pdf' | 'text' | 'binary' {
  const e = extOf(name)
  if (IMG_EXT.includes(e)) return 'image'
  if (VID_EXT.includes(e)) return 'video'
  if (AUD_EXT.includes(e)) return 'audio'
  if (e === 'pdf') return 'pdf'
  if (e === '' || TEXT_EXT.includes(e)) return 'text'   // no-extension files: try as text
  return 'binary'
}

function FilesWindow() {
  const [cwd, setCwd] = useState('')
  const [entries, setEntries] = useState<FileNode[] | null>(null)
  const [sel, setSel] = useState<FileNode | null>(null)
  const [content, setContent] = useState('')
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({})

  const load = useCallback((path: string) => {
    setEntries(null); setMsg('')
    listFiles(path).then(setEntries).catch((e) => { setEntries([]); setMsg(friendlyError((e as Error).message)) })
  }, [])
  useEffect(() => { load(cwd) }, [cwd, load])

  const isAbs = cwd.startsWith('/')
  const segs = cwd.split('/').filter(Boolean)
  const goUp = () => { const i = cwd.lastIndexOf('/'); setSel(null); setCwd(i <= 0 ? '' : cwd.slice(0, i)) }
  const crumbTo = (i: number) => { const j = segs.slice(0, i + 1).join('/'); return isAbs ? '/' + j : j }

  const open = (n: FileNode) => {
    if (n.directory) { setSel(null); setCwd(n.path); return }
    setSel(n); setMsg('')
    if (fileKind(n.name) !== 'text') { setContent(''); return }   // image/video/audio/pdf/binary → /raw or fallback
    setBusy(true)
    getFileContent(n.path)
      .then((f) => { setContent(f.content); setDirty(false) })
      .catch((er) => setMsg(friendlyError((er as Error).message)))
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
  const openLocation = () => { if (sel) revealFile(sel.path).catch(() => setMsg('Could not reveal the file.')) }

  const kind = sel ? fileKind(sel.name) : 'text'

  return (
    <div className="files">
      <div className="files-bar">
        <button className="hint" disabled={!cwd} onClick={goUp}>↑ Up</button>
        <span className="crumbs"><button className="crumb" onClick={() => { setSel(null); setCwd('') }}>Explorer</button>
          {segs.map((c, i) => <button key={i} className="crumb" onClick={() => { setSel(null); setCwd(crumbTo(i)) }}>/ {c}</button>)}</span>
        <span className="grow" />
        {msg && <span className="files-msg">{msg}</span>}
      </div>
      <div className="files-split">
        <div className="files-list">
          {!entries ? <div className="w-empty"><span className="spin-fast">◠</span></div>
            : entries.length === 0 ? <div className="w-empty"><div className="s">Empty folder.</div></div>
            : cwd === '' ? (() => {
                const fileRow = (n: FileNode) => (
                  <button key={n.path} className={`file-row${sel?.path === n.path ? ' sel' : ''}`} onClick={() => open(n)}>
                    <span className="fic">{n.directory ? '📁' : '📄'}</span><span className="fname">{n.name}</span>
                    <span className="fsize">{n.directory ? '' : kb(n.size)}</span></button>)
                const section = (title: string, items: FileNode[]) => (
                  <div key={title}>
                    <button className="files-group" onClick={() => setCollapsed((c) => ({ ...c, [title]: !c[title] }))}>
                      <span className="chev">{collapsed[title] ? '▸' : '▾'}</span>{title}<span className="cnt">{items.length}</span>
                    </button>
                    {!collapsed[title] && items.map(fileRow)}
                  </div>)
                const drive = entries.filter((n) => !n.path.startsWith('/'))   // jarvis_drive (relative)
                const os = entries.filter((n) => n.path.startsWith('/'))       // OS mounts (absolute)
                return <>
                  {section('jarvis_drive', drive)}
                  {os.length > 0 && section('OS', os)}
                </>
              })()
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
                <button className="hint" onClick={openLocation} title="Reveal in Finder">Open location</button>
                {kind === 'text' && <button className="hint" disabled={busy || !dirty} onClick={save}>{busy ? '…' : dirty ? 'Save' : 'Saved'}</button>}</div>
              {kind === 'image' ? <div className="files-preview"><img src={rawUrl(sel.path)} alt={sel.name} /></div>
                : kind === 'video' ? <div className="files-preview"><video src={rawUrl(sel.path)} controls /></div>
                : kind === 'audio' ? <div className="files-preview"><audio src={rawUrl(sel.path)} controls /></div>
                : kind === 'pdf' ? <iframe className="files-preview" src={rawUrl(sel.path)} title={sel.name} />
                : kind === 'binary' ? <div className="w-empty"><div className="big">📦</div><div className="s">No inline preview for this file type.<br />Use “Open location” to open it in Finder.</div></div>
                : busy ? <div className="w-empty"><span className="spin-fast">◠</span></div>
                : <textarea className="files-edit" value={content} spellCheck={false}
                    onChange={(e) => { setContent(e.target.value); setDirty(true) }} />}
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

function PluginsWindow() {
  const [installed, setInstalled] = useState<PluginInfo[] | null>(null)
  const [catalog, setCatalog] = useState<PluginCatalogEntry[] | null>(null)
  const [busy, setBusy] = useState('')
  const [msg, setMsg] = useState('')
  const refresh = useCallback(() => {
    getInstalledPlugins().then(setInstalled).catch(() => setInstalled([]))
    getPluginCatalog().then(setCatalog).catch(() => setCatalog([]))
  }, [])
  useEffect(() => { refresh() }, [refresh])

  const install = (id: string) => {
    setBusy(id); setMsg('')
    installPlugin({ id }).then((added) => { setMsg(`Installed ${added.map(p => p.name).join(', ')}`); refresh() })
      .catch((e) => setMsg(friendlyError((e as Error).message))).finally(() => setBusy(''))
  }
  const remove = (id: string, name: string) => {
    if (!confirm(`Uninstall "${name}"? Its tools will be removed from Jarvis.`)) return
    setBusy(id); setMsg('')
    uninstallPlugin(id).then((r) => { setMsg(r.message); refresh() })
      .catch((e) => setMsg(friendlyError((e as Error).message))).finally(() => setBusy(''))
  }
  const notInstalled = (catalog ?? []).filter((c) => !c.installed)

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        {msg && <span className="files-msg">{msg}</span>}
      </div>
      <div className="dv-sec-title">Installed</div>
      {!installed ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : installed.length === 0 ? <div className="w-empty"><div className="s">No plugins installed. Install one from the marketplace below.</div></div>
        : <div className="rows">{installed.map((p) => (
            <div className="row" key={p.id}>
              <span className="grow"><strong>{p.name}</strong> <span style={{ color: 'var(--muted)' }}>v{p.version}</span>
                <div style={{ color: 'var(--muted)', fontSize: 12 }}>{p.description}</div>
                <div style={{ fontSize: 11, marginTop: 2 }}>{p.tools.map((t) => <span key={t} className="pill low" style={{ marginRight: 4 }}>{t}</span>)}</div></span>
              <button className="hint" disabled={busy === p.id} onClick={() => remove(p.id, p.name)}>{busy === p.id ? '…' : 'Uninstall'}</button>
            </div>))}</div>}

      <div className="dv-sec-title" style={{ marginTop: 16 }}>Marketplace</div>
      {!catalog ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : notInstalled.length === 0 ? <div className="w-empty"><div className="s">{(catalog.length ?? 0) === 0 ? 'No catalog configured (add a catalog.json in the Plugins folder).' : 'Everything in the catalog is installed.'}</div></div>
        : <div className="rows">{notInstalled.map((c) => (
            <div className="row" key={c.id}>
              <span className="grow"><strong>{c.name}</strong> <span style={{ color: 'var(--muted)' }}>v{c.version}</span>
                <div style={{ color: 'var(--muted)', fontSize: 12 }}>{c.description}</div></span>
              <button className="hint" disabled={busy === c.id} onClick={() => install(c.id)}>{busy === c.id ? '…' : 'Install'}</button>
            </div>))}</div>}
    </>
  )
}

const CHART_COLORS = ['#45d6ff', '#3ad29f', '#ffb454', '#ff6b81', '#b58cff', '#7bd0ff', '#8effc1']

function Donut({ data }: { data: { label: string; value: number }[] }) {
  const total = data.reduce((s, d) => s + d.value, 0) || 1
  const r = 52, circ = 2 * Math.PI * r
  let off = 0
  return (
    <svg viewBox="0 0 130 130" width={130} height={130} style={{ flex: 'none' }}>
      <g transform="translate(65,65) rotate(-90)">
        <circle r={r} fill="none" stroke="rgba(120,150,190,0.12)" strokeWidth={16} />
        {data.map((d, i) => {
          const len = (d.value / total) * circ
          const seg = <circle key={i} r={r} fill="none" stroke={CHART_COLORS[i % CHART_COLORS.length]}
            strokeWidth={16} strokeDasharray={`${len} ${circ - len}`} strokeDashoffset={-off} />
          off += len
          return seg
        })}
      </g>
    </svg>
  )
}

function Spark({ points }: { points: number[] }) {
  if (points.length < 2) return <div className="note">Not enough runs yet for a trend.</div>
  const w = 360, h = 56, max = Math.max(...points, 1), step = w / (points.length - 1)
  const d = points.map((p, i) => `${i ? 'L' : 'M'}${(i * step).toFixed(1)},${(h - (p / max) * h).toFixed(1)}`).join(' ')
  const area = `${d} L${w},${h} L0,${h} Z`
  return (
    <svg viewBox={`0 0 ${w} ${h}`} width="100%" height={h} preserveAspectRatio="none">
      <path d={area} fill="rgba(69,214,255,0.10)" />
      <path d={d} fill="none" stroke="var(--accent)" strokeWidth={1.5} />
    </svg>
  )
}

const PROVIDER_LABEL: Record<string, string> = { ollama: 'Ollama', openai: 'OpenAI', anthropic: 'Anthropic', mock: 'Mock' }
const provLabel = (p: string | null) => (p ? PROVIDER_LABEL[p] ?? p : '—')

/** Horizontal input-vs-output split bar. */
function SplitBar({ inTok, outTok }: { inTok: number; outTok: number }) {
  const total = inTok + outTok || 1
  return (
    <div>
      <div className="splitbar">
        <span className="in" style={{ width: `${(inTok / total) * 100}%` }} />
        <span className="out" style={{ width: `${(outTok / total) * 100}%` }} />
      </div>
      <div className="splitbar-legend">
        <span><i className="sw in" /> Input {inTok.toLocaleString()} ({Math.round((inTok / total) * 100)}%)</span>
        <span><i className="sw out" /> Output {outTok.toLocaleString()} ({Math.round((outTok / total) * 100)}%)</span>
      </div>
    </div>
  )
}

function shortTime(at: string | null): string {
  if (!at) return '—'
  try { return new Date(at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) } catch { return '—' }
}

function TokensWindow() {
  const [data, setData] = useState<TokenDashboard | null>(null)
  const [budget, setBudgetState] = useState<SettingsView['budget'] | null>(null)
  const [sel, setSel] = useState<string | null>(null)   // selected provider, or null = All
  const refresh = useCallback(() => {
    getTokenDashboard(300).then(setData).catch(() => setData(null))
    getSettings().then((s) => setBudgetState(s.budget ?? null)).catch(() => {})
  }, [])
  useEffect(() => { refresh() }, [refresh])

  if (!data) return <div className="w-empty"><span className="spin-fast">◠</span></div>
  const providers = data.providers
  const pie = providers.filter((m) => m.totalTokens > 0).map((m) => ({ label: m.provider, value: m.totalTokens }))
  const maxTokens = Math.max(1, ...providers.map((m) => m.totalTokens))
  const maxCost = Math.max(0.0000001, ...providers.map((m) => m.cost))
  const colorOf = (provider: string) => CHART_COLORS[Math.max(0, providers.findIndex((m) => m.provider === provider)) % CHART_COLORS.length]
  const detail = sel ? providers.find((m) => m.provider === sel) : null
  const recent = [...data.timeline].reverse().filter((t) => !detail || t.provider === detail.provider).slice(0, 14)

  const runsTable = (
    <table className="tok-table">
      <thead><tr><th>Time</th><th>Provider</th><th>Model</th><th className="num">Tokens</th><th className="num">Cost</th></tr></thead>
      <tbody>
        {recent.length === 0 ? <tr><td colSpan={5} className="empty">No runs yet on a real AI provider.</td></tr>
          : recent.map((t, i) => (
            <tr key={i}>
              <td className="mono">{shortTime(t.at)}</td>
              <td><span className="dot" style={{ background: colorOf(t.provider ?? '') }} /> {provLabel(t.provider)}</td>
              <td className="mono dim">{t.model ?? '—'}</td>
              <td className="num">{t.tokens.toLocaleString()}</td>
              <td className="num dim">${t.cost.toFixed(4)}</td>
            </tr>))}
      </tbody>
    </table>
  )

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        <span className="note">{data.runs} AI runs analysed</span>
      </div>

      <div className="tok-tabs">
        <button className={`tok-tab${sel === null ? ' on' : ''}`} onClick={() => setSel(null)}>All providers</button>
        {providers.map((m) => (
          <button key={m.provider} className={`tok-tab${sel === m.provider ? ' on' : ''}`} onClick={() => setSel(m.provider)}>
            <span className="dot" style={{ background: colorOf(m.provider) }} />{provLabel(m.provider)}
          </button>
        ))}
      </div>

      {detail ? (
        /* ----- per-provider detail ----- */
        <>
          <div className="cards3">
            <div className="scard"><div className="lbl">TOTAL TOKENS</div><div className="big">{detail.totalTokens.toLocaleString()}</div><div className="sub">{Math.round((detail.totalTokens / Math.max(1, data.totalTokens)) * 100)}% of all usage</div></div>
            <div className="scard"><div className="lbl">EST. COST</div><div className="big">${detail.cost.toFixed(4)}</div><div className="sub">{detail.runs.toLocaleString()} runs</div></div>
            <div className="scard"><div className="lbl">AVG / RUN</div><div className="big">{Math.round(detail.totalTokens / Math.max(1, detail.runs)).toLocaleString()}</div><div className="sub">tokens per run</div></div>
          </div>
          <div className="dv-sec-title" style={{ marginTop: 14 }}>Input vs output · {provLabel(detail.provider)}</div>
          <SplitBar inTok={detail.promptTokens} outTok={detail.completionTokens} />
          <div className="dv-sec-title" style={{ marginTop: 16 }}>Tokens per run</div>
          <Spark points={data.timeline.filter((t) => t.provider === detail.provider).map((t) => t.tokens)} />
          <div className="dv-sec-title" style={{ marginTop: 16 }}>Recent {provLabel(detail.provider)} runs</div>
          {runsTable}
        </>
      ) : (
        /* ----- all-providers overview ----- */
        <>
          <div className="cards3">
            <div className="scard"><div className="lbl">TOTAL TOKENS</div><div className="big">{data.totalTokens.toLocaleString()}</div><div className="sub">{data.promptTokens.toLocaleString()} in · {data.completionTokens.toLocaleString()} out</div></div>
            <div className="scard"><div className="lbl">EST. COST</div><div className="big">${data.totalCost.toFixed(4)}</div><div className="sub">paid providers only</div></div>
            <div className="scard"><div className="lbl">BUDGET TODAY</div><div className="big">{budget && budget.dailyTokenBudget > 0 ? `${Math.round((budget.tokensToday / budget.dailyTokenBudget) * 100)}%` : '∞'}</div><div className="sub">{budget ? (budget.paused ? 'PAUSED' : budget.dailyTokenBudget > 0 ? `${budget.tokensToday.toLocaleString()} / ${budget.dailyTokenBudget.toLocaleString()}` : 'no cap') : '—'}</div></div>
          </div>
          {budget && budget.dailyTokenBudget > 0 && (
            <div className="budget-meter"><div className="fill" style={{ width: `${Math.min(100, (budget.tokensToday / budget.dailyTokenBudget) * 100)}%` }} /></div>
          )}

          <div className="dv-sec-title" style={{ marginTop: 14 }}>Token share by provider <span className="note">· click to drill in</span></div>
          <div className="tok-split">
            <Donut data={pie.length ? pie : [{ label: 'none', value: 1 }]} />
            <div className="tok-legend">
              {providers.map((m) => (
                <button className="tok-row" key={m.provider} onClick={() => setSel(m.provider)}>
                  <span className="dot" style={{ background: colorOf(m.provider) }} />
                  <span className="nm">{provLabel(m.provider)}</span>
                  <span className="bar"><span style={{ width: `${(m.totalTokens / maxTokens) * 100}%`, background: colorOf(m.provider) }} /></span>
                  <span className="val">{m.totalTokens.toLocaleString()}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="dv-sec-title" style={{ marginTop: 16 }}>Input vs output</div>
          <SplitBar inTok={data.promptTokens} outTok={data.completionTokens} />

          <div className="dv-sec-title" style={{ marginTop: 16 }}>Cost by provider</div>
          <div className="tok-legend">
            {providers.map((m) => (
              <div className="tok-row" key={m.provider} style={{ cursor: 'default' }}>
                <span className="dot" style={{ background: colorOf(m.provider) }} />
                <span className="nm">{provLabel(m.provider)}</span>
                <span className="bar"><span style={{ width: `${(m.cost / maxCost) * 100}%`, background: colorOf(m.provider) }} /></span>
                <span className="val">${m.cost.toFixed(4)}</span>
              </div>
            ))}
          </div>

          <div className="dv-sec-title" style={{ marginTop: 16 }}>Provider breakdown</div>
          <table className="tok-table">
            <thead><tr><th>Provider</th><th className="num">Runs</th><th className="num">Input</th><th className="num">Output</th><th className="num">Total</th><th className="num">Cost</th></tr></thead>
            <tbody>
              {providers.map((m) => (
                <tr key={m.provider}>
                  <td><span className="dot" style={{ background: colorOf(m.provider) }} /> {provLabel(m.provider)}</td>
                  <td className="num">{m.runs.toLocaleString()}</td>
                  <td className="num dim">{m.promptTokens.toLocaleString()}</td>
                  <td className="num dim">{m.completionTokens.toLocaleString()}</td>
                  <td className="num">{m.totalTokens.toLocaleString()}</td>
                  <td className="num dim">${m.cost.toFixed(4)}</td>
                </tr>))}
            </tbody>
          </table>

          <div className="dv-sec-title" style={{ marginTop: 16 }}>Tokens per run (recent → now)</div>
          <Spark points={data.timeline.map((t) => t.tokens)} />

          <div className="dv-sec-title" style={{ marginTop: 16 }}>Recent activity</div>
          {runsTable}
        </>
      )}
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
  const [saved, setSaved] = useState(false)

  // Each provider keeps its own model field (openai-model vs ollama-model vs Anthropic model).
  const modelFor = (s: SettingsView) => s.provider === 'openai' ? (s.openaiModel ?? '')
    : s.provider === 'ollama' ? (s.ollamaModel ?? '') : s.model

  useEffect(() => { getSettings().then((s) => { setSettings(s); setProviderState(s.provider); setModel(modelFor(s)) }).catch(() => {}) }, [])

  const changeProvider = (p: string) => {
    setProviderState(p); savePref('jarvis.provider', p); setSaving(true)
    // Switch provider only — don't overwrite the new provider's model with the old one's value.
    apiSetProvider(p).then((s) => { setSettings(s); setProviderState(s.provider); setModel(modelFor(s)) }).catch(() => {}).finally(() => setSaving(false))
  }

  // Settings apply on change; Save re-confirms provider+model and flashes a confirmation.
  const saveAll = () => {
    setSaving(true)
    apiSetProvider(provider, model || undefined)
      .then((s) => { setSettings(s); setModel(modelFor(s)); setSaved(true); setTimeout(() => setSaved(false), 2000) })
      .catch(() => {})
      .finally(() => setSaving(false))
  }
  const providerLabel = (p?: string) => p === 'claude' ? 'Anthropic' : p === 'ollama' ? 'Ollama' : p === 'openai' ? 'OpenAI' : 'Mock'

  return (
    <>
      <div className="cards3">
        <div className="scard"><div className="lbl">AI PROVIDER</div><div className="big">{providerLabel(settings?.provider ?? provider)}</div><div className="sub">{(settings?.provider ?? provider) === 'ollama' ? 'local · ' + (settings?.ollamaModel ?? 'ollama') : settings?.hasAnthropicKey ? 'API key set' : 'no key · offline'}</div></div>
        <div className="scard"><div className="lbl">STT ENGINE</div><div className="big">{stt === 'whisper' ? 'Whisper' : 'Local'}</div><div className="sub">{stt === 'whisper' ? 'cloud · accurate' : 'offline'}</div></div>
        <div className="scard"><div className="lbl">TTS ENGINE</div><div className="big">{tts === 'system' ? 'System' : 'ElevenLabs'}</div><div className="sub">{tts === 'system' ? 'native · fastest' : 'cloud'}</div></div>
      </div>

      <div className="field"><label>AI provider {saving && <span className="spin-fast">◠</span>}</label>
        <select value={provider} onChange={(e) => changeProvider(e.target.value)}>
          <option value="ollama">Ollama (local · agentic · no key)</option>
          <option value="openai">OpenAI (cloud · needs key)</option>
          <option value="claude">Anthropic Claude (cloud · needs key)</option>
          <option value="mock">Mock (offline stub · scripted)</option>
        </select>
        <div className="note">{provider === 'claude' && !settings?.hasAnthropicKey ? 'No ANTHROPIC_API_KEY set — calls fall back to the offline mock.' : provider === 'openai' && !settings?.hasOpenaiKey ? 'No OPENAI_API_KEY set — calls fall back to the offline mock.' : provider === 'ollama' ? 'Uses your local Ollama; falls back to the mock if it is not running.' : 'Takes effect immediately for new requests.'}</div></div>
      <div className="field"><label>Model</label>
        <input value={model} onChange={(e) => setModel(e.target.value)} onBlur={() => apiSetProvider(provider, model).then(setSettings).catch(() => {})} placeholder="llama3.2:3b" /></div>

      <div className="field"><label>Daily token budget — paid providers only (0 = unlimited) {settings?.budget?.paused && <span style={{ color: 'var(--bad)' }}>· PAUSED</span>}</label>
        <input type="number" min={0} step={10000} defaultValue={settings?.budget?.dailyTokenBudget ?? 0}
          onBlur={(e) => apiSetBudget({ dailyTokenBudget: Math.max(0, Number(e.target.value) || 0) }).then(setSettings).catch(() => {})}
          placeholder="0" />
        <div className="note">
          {settings?.budget
            ? `Used today: ${settings.budget.tokensToday.toLocaleString()} tokens` + (settings.budget.dailyTokenBudget > 0 ? ` · ${settings.budget.remaining.toLocaleString()} left` : ' · no cap')
            : 'Caps Claude/OpenAI spend per day. Ollama & Mock are free and never metered.'}
        </div>
        <div className="seg" style={{ marginTop: 8 }}>
          <button className={!settings?.budget?.paused ? 'on' : ''} onClick={() => apiSetBudget({ paused: false }).then(setSettings).catch(() => {})}><span className="pip" />AI on</button>
          <button className={settings?.budget?.paused ? 'on' : ''} onClick={() => apiSetBudget({ paused: true }).then(setSettings).catch(() => {})}><span className="pip" />Pause (kill-switch)</button>
        </div></div>

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

      <div className="settings-foot">
        <span className="note" style={{ flex: 1 }}>Settings apply as you change them. Save re-confirms the AI provider &amp; model.</span>
        <button className="hint primary" onClick={saveAll} disabled={saving}>{saving ? 'Saving…' : saved ? 'Saved ✓' : 'Save'}</button>
      </div>
    </>
  )
}

/** True when an "answer" is actually a raw tool-call / JsonNode dump that leaked through. */
function looksLikeRawTool(s?: string): boolean {
  if (!s) return false
  const t = s.trim()
  if (/"nodeType"\s*:\s*"OBJECT"|"bigDecimal"\s*:|"valueNode"\s*:/.test(t)) return true
  return t.startsWith('{') && /"(name|tool|function)"\s*:/.test(t) && /"(parameters|arguments)"\s*:/.test(t)
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

/** The running chat transcript: one reused window, every exchange appended. */
function ConversationWindow({ turns, onClear }: { turns: Turn[]; onClear: () => void }) {
  const endRef = useRef<HTMLDivElement | null>(null)
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [turns])
  return (
    <div className="convo">
      <div className="convo-bar">
        <span className="grow" style={{ color: 'var(--muted)', fontSize: 12 }}>{turns.length} exchange{turns.length === 1 ? '' : 's'}</span>
        {turns.length > 0 && <button className="hint" onClick={onClear}>Clear view</button>}
      </div>
      <div className="convo-scroll">
        {turns.length === 0
          ? <div className="w-empty"><div className="big">💬</div><div className="s">Ask Jarvis anything from the bar below — your whole conversation shows up here.</div></div>
          : turns.map((t) => (
            <div className="turn" key={t.id}>
              <div className="bubble user"><span className="who">You</span><div className="txt">{t.prompt}</div></div>
              <div className="bubble jarvis">
                <span className="who">Jarvis</span>
                {t.steps.length > 0 && (
                  <div className="substeps">
                    {t.steps.map((s, i) => (
                      <div className="substep" key={i}><span className="kind">{s.kind}</span><span className="lbl">{s.label}{s.detail ? <span className="det"> — {s.detail}</span> : null}</span></div>
                    ))}
                    {t.loading && <div className="substep"><span className="kind"><span className="spin-fast">◠</span></span><span className="det">working…</span></div>}
                  </div>
                )}
                {t.loading && t.steps.length === 0 && <div className="w-empty" style={{ padding: 12 }}><span className="spin-fast">◠</span><div className="s">Jarvis is thinking…</div></div>}
                {t.resp && (isHtmlish(t.resp.answer) || looksLikeRawTool(t.resp.answer)
                  ? <ErrorCard message={looksLikeRawTool(t.resp.answer) ? "I had trouble using a tool for that. Try rephrasing, or switch the model in Settings." : t.resp.answer} />
                  : <><div className="answer-txt">{t.resp.answer}</div>
                      <div className="answer-meta"><span>{t.resp.agent}</span><span>model {t.resp.model}</span><span>{t.resp.tokens} tokens</span></div></>)}
                {t.commandResult && (t.commandResult.status === 'ERROR'
                  ? <ErrorCard message={t.commandResult.message} />
                  : <>
                      {t.commandResult.message && <div className="answer-txt">{isHtmlish(t.commandResult.message) ? stripHtml(t.commandResult.message) : t.commandResult.message}</div>}
                      {t.commandResult.data !== undefined && t.commandResult.data !== null && t.commandResult.data !== '' ? <DataView value={t.commandResult.data} /> : null}
                    </>)}
              </div>
            </div>))}
        <div ref={endRef} />
      </div>
    </div>
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
    case 'plugins': return <PluginsWindow />
    case 'tokens': return <TokensWindow />
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
  '/files': 'files', '/jfiles': 'files', '/backup': 'backups', '/backups': 'backups', '/plugins': 'plugins',
  '/tokens': 'tokens', '/costs': 'tokens',
}
const WIN_META: Record<WinKind, { title: string; subtitle: string; dim: string }> = {
  conversation: { title: 'Conversation', subtitle: 'Your chat with Jarvis', dim: '720×620' },
  today: { title: 'Jarvis Today', subtitle: 'Daily digest — counts, highlights', dim: '720×600' },
  memory: { title: 'Memory', subtitle: 'Trusted facts', dim: '720×640' },
  history: { title: 'Multi-step history', subtitle: 'Prompts and their sub-step trees', dim: '900×620' },
  settings: { title: 'Settings', subtitle: 'Voice · models · privacy', dim: '880×640' },
  agents: { title: 'Agents', subtitle: 'The roster', dim: '760×560' },
  logs: { title: 'Activity log', subtitle: 'Recent audited actions', dim: '760×560' },
  files: { title: 'Explorer', subtitle: 'Browse · view · edit files', dim: '880×620' },
  backups: { title: 'Backups', subtitle: 'Snapshot · restore the Explorer', dim: '720×560' },
  plugins: { title: 'Plugins', subtitle: 'Installed · marketplace', dim: '820×620' },
  tokens: { title: 'Token usage', subtitle: 'Spend & tokens per model', dim: '860×640' },
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
  const [turns, setTurns] = useState<Turn[]>([])
  const [listening, setListening] = useState(false)
  const [wake, setWake] = useState(false)
  const [notifOpen, setNotifOpen] = useState(false)
  const [notifItems, setNotifItems] = useState<NotificationItem[]>([])
  const [notifExpanded, setNotifExpanded] = useState<string | null>(null)
  const zRef = useRef(30)
  const wakeRef = useRef<any>(null)
  const esRef = useRef<EventSource | null>(null)   // current streamInput connection
  const ttsOn = pref('jarvis.tts', 'system') !== 'off'

  useEffect(() => {
    fetchCommands().then(setCommands).catch(() => {})
    getSettings().then(setSettings).catch(() => {})
    // Rehydrate the conversation transcript for this session (survives reloads).
    getConversation().then((hist) => {
      const seeded: Turn[] = []
      hist.forEach((h, i) => {
        if (h.role === 'USER') {
          seeded.push({ id: `h-${i}`, prompt: h.content, loading: false, steps: [] })
        } else if (seeded.length > 0 && !seeded[seeded.length - 1].resp && !seeded[seeded.length - 1].commandResult) {
          seeded[seeded.length - 1].commandResult = { status: 'OK', message: h.content }
        }
      })
      if (seeded.length > 0) setTurns(seeded)
    }).catch(() => {})
    const poll = () => { getStatus().then(setSnap).catch(() => {}); getUnreadCount().then(setUnread).catch(() => {}) }
    poll()
    const s = setInterval(poll, 3000)
    const c = setInterval(() => setNow(new Date()), 1000)
    const k = (e: KeyboardEvent) => { if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setCmdkOpen((o) => !o) } }
    window.addEventListener('keydown', k)
    return () => { clearInterval(s); clearInterval(c); window.removeEventListener('keydown', k); esRef.current?.close() }
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

  const updateTurn = useCallback((id: string, patch: Partial<Turn>) =>
    setTurns((ts) => ts.map((t) => t.id === id ? { ...t, ...patch } : t)), [])

  // Single cognitive door: raw input → backend InputRouter → command or streamed Brain.
  // All exchanges accumulate in ONE Conversation window (a running transcript).
  const askInput = useCallback((raw: string, spoken = false) => {
    setBusy(true)
    openWindow('conversation')              // singleton — reused for every turn
    esRef.current?.close()                  // close any prior stream before starting a new one
    const id = `t-${++zRef.current}`
    setTurns((ts) => [...ts, { id, prompt: raw, loading: true, steps: [] }])
    const collected: Step[] = []
    esRef.current = streamInput(raw, {
      onStep: (s) => { collected.push(s); updateTurn(id, { steps: [...collected] }) },
      onResult: (result: CommandResult) => {
        if (result.type === 'chat') {
          const resp = result.data as ChatResponse
          updateTurn(id, { loading: false, steps: [...collected], resp })
          if (spoken && ttsOn && !isHtmlish(resp.answer) && !looksLikeRawTool(resp.answer)) speak(resp.answer)
        } else {
          updateTurn(id, { loading: false, steps: [...collected], commandResult: { status: result.status, message: result.message, data: result.data } })
          if (spoken && ttsOn && result.status !== 'ERROR' && result.message) speak(result.message)
        }
      },
      onError: (m) => updateTurn(id, { loading: false, steps: [...collected], commandResult: { status: 'ERROR', message: m } }),
      onDone: () => setBusy(false),
    })
  }, [openWindow, updateTurn, ttsOn])

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
    const backend = ui === 'anthropic' ? 'claude' : ui   // UI label "anthropic" → backend provider "claude"
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
          <div className="telemetry-flag"><span className="pip" />TELEMETRY ACTIVE</div>
        </div>

        <div className="hud-tr">
          <div className="clock">{now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</div>
          <div className="datestr">{now.toLocaleDateString([], { weekday: 'short', month: 'short', day: '2-digit', year: 'numeric' }).toUpperCase()}</div>
          <div className="row2">
            <button className="iconbtn" title="Chat history" onClick={() => openWindow('conversation')}>💬</button>
            <button className="iconbtn" title="Notifications" onClick={() => setNotifOpen((o) => {
              const next = !o
              if (next) { setNotifExpanded(null); getNotifications().then(setNotifItems).catch(() => {}) }
              return next
            })}>
              🔔{unread > 0 && <span className="count">{unread}</span>}</button>
          </div>
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
          <button className="hint" onClick={() => openWindow('plugins')}>Plugins</button>
          <button className="hint" onClick={() => openWindow('tokens')}>Tokens</button>
          <button className="hint" onClick={() => openWindow('agents')}>Agents</button>
          <button className="hint" onClick={() => setCmdkOpen(true)}>⌘K</button>
        </div>
      </div>

      {/* floating windows */}
      <div className="winlayer">
        {wins.map((w) => (
          <FloatingWindow key={w.key} win={w} onClose={() => closeWin(w.key)} onFocus={() => focusWin(w.key)}>
            {w.kind === 'conversation'
              ? <ConversationWindow turns={turns} onClear={() => setTurns([])} />
              : <WindowBody win={w} />}
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
