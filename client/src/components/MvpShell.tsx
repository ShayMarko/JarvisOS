import { useEffect, useMemo, useState } from 'react'
import '../mvp.css'
import { getAgents, getApprovals, getConnectors, getInstalledPlugins, getMemoryList, getRoi } from '../api'
import type { AgentDef, ConnectorInfo, MonitorSnapshot, SettingsView, Step } from '../api'
import type { Turn, Win, WinKind } from '../types'
import { gb, pct } from '../lib/format'
import { useFetch } from '../lib/useFetch'

/* eslint-disable @typescript-eslint/no-explicit-any */

interface MenuItem { t: string; ic: string; kind: WinKind; tab?: string; cnt?: string; cntKind?: 'crit' | 'good'; flyout?: boolean }

const MENU: { grp: string; items: MenuItem[] }[] = [
  { grp: 'Workspace', items: [
    { t: 'Timeline', ic: '◷', kind: 'activity', tab: 'timeline' },
  ] },
]

// ---- live-flow brain graph -------------------------------------------------
type GType = 'orch' | 'intent' | 'hub' | 'agent' | 'conn' | 'cap' | 'memory' | 'plugin'
interface GNode { id: string; x: number; y: number; type: GType; label: string; hub?: string; kind?: WinKind; tab?: string }

/** Gentle curved path between two points (perpendicular bow) — organic neural look. */
function curve(x1: number, y1: number, x2: number, y2: number, k: number) {
  const mx = (x1 + x2) / 2, my = (y1 + y2) / 2, dx = x2 - x1, dy = y2 - y1
  return `M${x1.toFixed(1)} ${y1.toFixed(1)} Q ${(mx - dy * k).toFixed(1)} ${(my + dx * k).toFixed(1)} ${x2.toFixed(1)} ${y2.toFixed(1)}`
}

function makeRng(seed: number) {
  let s = seed >>> 0
  return () => { s |= 0; s = (s + 0x6d2b79f5) | 0; let t = Math.imul(s ^ (s >>> 15), 1 | s); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296 }
}

const trunc = (s: string, n: number) => (s.length > n ? s.slice(0, n - 1) + '…' : s)

/** Group a raw tool name into a human "capability" category. */
function capCategory(toolLower: string): string {
  const t = toolLower
  if (/file|fs_|read|write|dir|path|explor/.test(t)) return 'Files'
  if (/web|http|url|fetch|search|browse|playwright/.test(t)) return 'Web'
  if (/shell|exec|run_|command|sandbox|terminal|process/.test(t)) return 'Shell'
  if (/image|vision|screenshot|sips|describe|photo/.test(t)) return 'Vision'
  if (/doc|pdf|markdown|report|book|article|page/.test(t)) return 'Docs'
  if (/memory|kb|knowledge|index|recall|embed|note/.test(t)) return 'Memory'
  if (/mail|email|gmail|slack|discord|telegram|notify|message|sms|whatsapp/.test(t)) return 'Comms'
  if (/git|deploy|netlify|cloudflare|build|code|api/.test(t)) return 'Dev'
  if (/money|revenue|stripe|gumroad|lemon|invoice|finance|roi|sell/.test(t)) return 'Money'
  if (/calendar|schedule|cron|book|meeting|reminder/.test(t)) return 'Schedule'
  if (/token|cost|budget|model|usage/.test(t)) return 'Models'
  if (/notion|youtube|reddit|rss|connector/.test(t)) return 'Connect'
  return 'Other'
}

/** Lay out the entities to FILL a two-hemisphere brain silhouette: orchestrator at the core,
 *  intent up front, each domain occupying a brain region (agents=top, caps=left, conns=right),
 *  bound together by curved synapse tissue so it reads as one organ. */
function layout(agents: AgentDef[], connectors: ConnectorInfo[], caps: string[], memCats: string[], plugins: string[]) {
  const rnd = makeRng(20240610)
  const cx = 520, cy = 380, DX = 162, RX = 278, RY = 322
  const inBrain = (x: number, y: number) =>
    ((x - (cx - DX)) ** 2) / (RX * RX) + ((y - cy) ** 2) / (RY * RY) <= 1 ||
    ((x - (cx + DX)) ** 2) / (RX * RX) + ((y - cy) ** 2) / (RY * RY) <= 1
  const nodes: GNode[] = [{ id: 'orch', x: cx, y: cy, type: 'orch', label: 'Orchestrator' }]
  const edges: [string, string][] = []
  const INTENTS = ['Question', 'Command', 'Create', 'Research', 'Schedule', 'Money', 'Approve', 'Recall']
  // six lobes hexagonally around the core
  const clusters = [
    { hub: 'h-agents', label: 'Agents', tab: 'agents' as string | undefined, type: 'agent' as GType, ax: cx, ay: cy - 170, items: agents.map((a) => ({ id: 'a:' + a.slug, label: a.name })) },
    { hub: 'h-conns', label: 'Connectors', tab: 'connectors' as string | undefined, type: 'conn' as GType, ax: cx + 152, ay: cy - 86, items: connectors.map((c) => ({ id: 'c:' + c.id, label: c.name })) },
    { hub: 'h-plugins', label: 'Plugins', tab: 'plugins' as string | undefined, type: 'plugin' as GType, ax: cx + 152, ay: cy + 86, items: plugins.map((p) => ({ id: 'p:' + p, label: p })) },
    { hub: 'h-intent', label: 'Intent', tab: undefined as string | undefined, type: 'intent' as GType, ax: cx, ay: cy + 170, items: INTENTS.map((t) => ({ id: 'i:' + t, label: t })) },
    { hub: 'h-memory', label: 'Memory', tab: undefined as string | undefined, type: 'memory' as GType, ax: cx - 152, ay: cy + 86, items: memCats.map((t) => ({ id: 'm:' + t, label: t })) },
    { hub: 'h-caps', label: 'Capabilities', tab: undefined as string | undefined, type: 'cap' as GType, ax: cx - 152, ay: cy - 86, items: caps.map((t) => ({ id: 't:' + t, label: t })) },
  ]
  // Voronoi partition: each sampled point belongs to the nearest hub anchor → 4 contiguous lobes.
  const anchors = clusters.map((c) => ({ id: c.hub, x: c.ax, y: c.ay }))
  const nearestId = (x: number, y: number) => { let b = anchors[0], bd = 1e9; for (const a of anchors) { const d = (x - a.x) ** 2 + (y - a.y) ** 2; if (d < bd) { bd = d; b = a } } return b.id }
  const X0 = cx - DX - RX, XW = 2 * (DX + RX), Y0 = cy - RY, YH = 2 * RY
  // Poisson-disk spacing so dots don't pile on top of each other.
  const placed: { x: number; y: number }[] = clusters.map((c) => ({ x: c.ax, y: c.ay }))
  const far = (px: number, py: number, min: number) => { for (const p of placed) { const dx = px - p.x, dy = py - p.y; if (dx * dx + dy * dy < min * min) return false } return true }
  void X0; void XW; void Y0; void YH
  for (const cl of clusters) {
    nodes.push({ id: cl.hub, x: cl.ax, y: cl.ay, type: 'hub', label: cl.label, hub: cl.hub, kind: 'capabilities', tab: cl.tab })
    edges.push(['orch', cl.hub])
    // cluster the dots in a disc around THIS hub (radius scales with count) so each lobe reads as a group
    const R = Math.min(300, 70 + cl.items.length * 9)
    for (const it of cl.items) {
      let x = cl.ax, y = cl.ay, ok = false
      for (let tries = 0; tries < 420; tries++) {
        const min = tries < 260 ? 37 : tries < 350 ? 29 : 22
        const a = rnd() * Math.PI * 2, rr = Math.sqrt(rnd()) * R
        const px = cl.ax + rr * Math.cos(a), py = cl.ay + rr * Math.sin(a)
        if (inBrain(px, py) && nearestId(px, py) === cl.hub && Math.abs(px - cx) > 12 && ((px - cx) ** 2 + (py - cy) ** 2) > 54 * 54 && far(px, py, min)) { x = px; y = py; ok = true; break }
      }
      if (!ok) { for (let t = 0; t < 140; t++) { const a = rnd() * Math.PI * 2, rr = Math.sqrt(rnd()) * R, px = cl.ax + rr * Math.cos(a), py = cl.ay + rr * Math.sin(a); if (inBrain(px, py) && far(px, py, 15)) { x = px; y = py; break } } }
      placed.push({ x, y })
      nodes.push({ id: it.id, x, y, type: cl.type, label: it.label, hub: cl.hub, kind: 'capabilities', tab: cl.tab })
    }
  }
  // tissue synapses — 2 nearest neighbours per entity → visible curved nerve fibres
  const ent = nodes.filter((nn) => nn.type !== 'orch' && nn.type !== 'hub')
  const syn: [number, number, number, number][] = []
  for (let i = 0; i < ent.length; i++) {
    const d: [number, number][] = []
    for (let j = 0; j < ent.length; j++) { if (i === j) continue; const dx = ent[i].x - ent[j].x, dy = ent[i].y - ent[j].y; d.push([dx * dx + dy * dy, j]) }
    d.sort((a, b) => a[0] - b[0])
    for (let k = 0; k < 2; k++) { if (d[k] && d[k][0] < 13500) { const j = d[k][1]; syn.push([ent[i].x, ent[i].y, ent[j].x, ent[j].y]) } }
  }
  return { nodes, edges, syn }
}

/** Map a streamed step → the node id it lights up. */
function stepToId(step: Step | null | undefined, nodes: GNode[]): string | null {
  if (!step) return null
  const lab = (step.label || '').toLowerCase().trim()
  if (step.kind === 'agent') {
    const m = nodes.find((n) => n.type === 'agent' && (n.label.toLowerCase() === lab || n.id.slice(2) === lab || lab.includes(n.label.toLowerCase()) || n.label.toLowerCase().includes(lab)))
    return m?.id || 'h-agents'
  }
  if (step.kind === 'tool') {
    const p = nodes.find((n) => n.type === 'plugin' && (n.label.toLowerCase() === lab || lab.includes(n.label.toLowerCase()) || n.label.toLowerCase().includes(lab)))
    if (p) return p.id
    const cat = capCategory(lab)
    if (cat === 'Memory') return 'h-memory'
    const m = nodes.find((n) => n.type === 'cap' && n.id === 't:' + cat)
    return m?.id || 'h-caps'
  }
  if (step.kind === 'intent') return 'h-intent'
  return 'orch' // answer
}

export interface MvpShellProps {
  snap: MonitorSnapshot | null
  settings: SettingsView | null
  turns: Turn[]
  input: string
  busy: boolean
  listening: boolean
  unread: number
  wins: Win[]
  onInput: (v: string) => void
  onSubmit: (text: string) => void
  onMic: () => void
  onOpen: (kind: WinKind, payload?: unknown) => void
  onBell: () => void
  onFocusWin: (key: string) => void
  onCmdk: () => void
  wake: boolean
  onToggleWake: () => void
  uiProvider: string
  onProvider: (p: string) => void
}

export function MvpShell(props: MvpShellProps) {
  const { snap, settings, turns, input, busy, listening, unread, wins } = props
  const [overview, setOverview] = useState(false)
  const [now, setNow] = useState(() => new Date())

  const { data: agents } = useFetch(() => getAgents(), [], [])
  const { data: connectors } = useFetch(() => getConnectors(), [], [])
  const { data: approvals } = useFetch(() => getApprovals(), [], [])
  const { data: roi } = useFetch(() => getRoi(), null, [])
  const { data: memories } = useFetch(() => getMemoryList(''), [], [])
  const { data: plugins } = useFetch(() => getInstalledPlugins(), [], [])

  useEffect(() => { const i = setInterval(() => setNow(new Date()), 30000); return () => clearInterval(i) }, [])

  const agentCount = agents && agents.length ? agents.length : (snap?.runtime.registeredAgents ?? 51)
  const liveConn = connectors ? connectors.filter((c) => c.status === 'CONNECTED').length : 9
  const totalConn = connectors && connectors.length ? connectors.length : 26
  const pending = approvals ? approvals.filter((a) => a.status === 'PENDING').length : 3
  const revenue = roi ? Math.round(roi.revenue) : 3840
  const roiX = roi ? roi.roi.toFixed(1) : '4.2'
  const runningJobs = snap?.runtime.runningTasks ?? 2

  // build the live graph from real entities (capabilities grouped into categories; Memory + Plugins are their own lobes)
  const caps = useMemo(() => {
    const m = new Map<string, number>()
    ;(agents || []).forEach((a) => (a.toolNames || []).forEach((t) => { const c = capCategory(t.toLowerCase()); if (c !== 'Memory') m.set(c, (m.get(c) || 0) + 1) }))
    return [...m.keys()]
  }, [agents])
  const memCats = useMemo(() => {
    const s = new Set<string>(); (memories || []).forEach((m: any) => { if (m.category) s.add(m.category) })
    const a = [...s].slice(0, 10)
    return a.length ? a : ['Facts', 'Preferences', 'People', 'Projects', 'Habits', 'Goals']
  }, [memories])
  const pluginNames = useMemo(() => (plugins && plugins.length ? plugins.map((p: any) => p.name).slice(0, 12) : []), [plugins])
  const graph = useMemo(() => layout(agents || [], connectors || [], caps, memCats, pluginNames), [agents, connectors, caps, memCats, pluginNames])
  const nodeById = useMemo(() => { const m: Record<string, GNode> = {}; graph.nodes.forEach((n) => { m[n.id] = n }); return m }, [graph])
  const orch = nodeById['orch']

  // ---- live end-to-end flow: play the whole path of evaluated entities -----
  const flowTurn = turns[turns.length - 1]
  const loading = !!(flowTurn && flowTurn.loading)
  const stepsLen = flowTurn?.steps?.length ?? 0
  const pathIds = useMemo(() => {
    if (!flowTurn) return [] as string[]
    const raw = (flowTurn.steps || []).map((s) => stepToId(s, graph.nodes)).filter(Boolean) as string[]
    const out: string[] = []
    for (const id of raw) if (out[out.length - 1] !== id) out.push(id)
    return out
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flowTurn?.id, stepsLen, graph])
  const [reveal, setReveal] = useState(0)
  // live: while a turn is running, reveal every step streamed so far
  useEffect(() => { if (loading) setReveal(pathIds.length) }, [loading, pathIds.length])
  // on completion: replay the sequence end-to-end, hold a few seconds, then clear
  useEffect(() => {
    if (loading || pathIds.length === 0) return
    let i = 0; setReveal(0)
    const iv = setInterval(() => { i++; setReveal(i); if (i >= pathIds.length) clearInterval(iv) }, 430)
    const to = setTimeout(() => setReveal(0), pathIds.length * 430 + 4500)
    return () => { clearInterval(iv); clearTimeout(to) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flowTurn?.id, loading])

  const revealed = pathIds.slice(0, reveal)
  const chain = new Set<string>()
  revealed.forEach((id) => { chain.add(id); const n = nodeById[id]; if (n?.hub) chain.add(n.hub) })
  if (revealed.length || busy) chain.add('orch')
  const current = revealed[revealed.length - 1] || (busy ? 'orch' : null)
  const currentNode = current ? nodeById[current] : null
  const pulsePath = (() => {
    if (!currentNode || currentNode.id === 'orch') return ''
    const seg = (a: GNode, b: GNode) => { const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2, dx = b.x - a.x, dy = b.y - a.y; return ` Q ${(mx - dy * 0.1).toFixed(1)} ${(my + dx * 0.1).toFixed(1)} ${b.x.toFixed(1)} ${b.y.toFixed(1)}` }
    if (currentNode.type === 'hub') return `M${orch.x} ${orch.y}` + seg(orch, currentNode)
    const h = currentNode.hub ? nodeById[currentNode.hub] : null
    return h ? `M${orch.x} ${orch.y}` + seg(orch, h) + seg(h, currentNode) : `M${orch.x} ${orch.y}` + seg(orch, currentNode)
  })()
  const intentCount = useMemo(() => graph.nodes.filter((n) => n.type === 'intent').length, [graph])
  const hubCount = (id: string) => id === 'h-agents' ? String(agentCount) : id === 'h-conns' ? `${liveConn}/${totalConn}` : id === 'h-caps' ? String(caps.length) : id === 'h-intent' ? String(intentCount) : id === 'h-memory' ? String(memCats.length) : id === 'h-plugins' ? String(pluginNames.length) : ''
  const restR = (n: GNode) => n.type === 'orch' ? 16 : n.type === 'hub' ? 9 : 3.2
  const hotR = (n: GNode, cur: boolean) => n.type === 'orch' ? 18 : n.type === 'hub' ? 11 : (cur ? 7.5 : 5.5)
  const labelInfo = (n: GNode) => {
    const isEntity = n.type === 'agent' || n.type === 'conn' || n.type === 'cap' || n.type === 'intent' || n.type === 'memory' || n.type === 'plugin'
    const above = isEntity && (Math.round(n.x + n.y) % 2 === 0)
    const ly = n.type === 'orch' ? n.y + 30 : n.type === 'hub' ? n.y - 16 : (above ? n.y - 7 : n.y + 9)
    const label = n.type === 'hub' ? `${n.label} · ${hubCount(n.id)}` : isEntity ? trunc(n.label, 14) : n.label
    return { isEntity, ly, label }
  }

  // static brain layer — memoised so streaming steps don't re-render 100+ nodes (keeps FPS up)
  const countsKey = `${agentCount}|${liveConn}/${totalConn}|${caps.length}`
  const baseLayer = useMemo(() => (
    <>
      {graph.syn.map((s, i) => <path key={'s' + i} d={curve(s[0], s[1], s[2], s[3], (i % 2 ? 1 : -1) * 0.17)} className="mvp-syn" />)}
      {graph.edges.map(([a, b], i) => { const na = nodeById[a], nb = nodeById[b]; if (!na || !nb) return null; return <path key={'e' + i} d={curve(na.x, na.y, nb.x, nb.y, 0.13)} className="mvp-edge" /> })}
      {['h-agents', 'h-conns', 'h-caps', 'h-intent', 'h-memory', 'h-plugins'].map((id, i) => { const h = nodeById[id]; if (!h) return null; return <circle key={'amb' + id} r="2.4" className="mvp-amb"><animateMotion dur="2.8s" begin={`${i * 0.5}s`} repeatCount="indefinite" path={curve(orch.x, orch.y, h.x, h.y, 0.13)} /></circle> })}
      <circle cx={orch.x} cy={orch.y} className="mvp-orch-ring" />
      {graph.nodes.map((n) => { const { isEntity, ly, label } = labelInfo(n); return (
        <g key={n.id} data-nid={n.id} className="mvp-gnode">
          <circle cx={n.x} cy={n.y} r={restR(n)} className={`mvp-dot t-${n.type}`} />
          <text x={n.x} y={ly} textAnchor="middle" className={`mvp-glabel${isEntity ? ' sm' : ''}`}>{label}</text>
        </g>) })}
    </>
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ), [graph, countsKey])
  const onSvgClick = (e: any) => { const g = (e.target as Element).closest('[data-nid]'); const id = g?.getAttribute('data-nid'); const n = id ? nodeById[id] : null; if (n) { if (n.id === 'orch' || n.id === 'intent') props.onOpen('activity', 'runs'); else props.onOpen(n.kind || 'capabilities', n.tab) } }

  const hour = now.getHours()
  const part = hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening'
  const clock = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  const date = now.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' })

  const cpu = pct(snap?.cpu.systemCpuLoad)
  const mem = snap ? `${gb(snap.memory.usedPhysicalBytes)}G` : '6.1G'
  const model = settings?.model ?? 'Opus'
  const bud = settings?.budget
  const budPct = bud && bud.dailyTokenBudget > 0 ? Math.min(100, Math.round((bud.tokensToday / bud.dailyTokenBudget) * 100)) : 72
  const budLabel = bud && bud.dailyTokenBudget > 0 ? `${(bud.tokensToday / 1000).toFixed(0)}k/${(bud.dailyTokenBudget / 1000).toFixed(0)}k` : '$58/$80'

  return (
    <div className="mvp">
      <div className="mvp-fr tl" /><div className="mvp-fr tr" /><div className="mvp-fr bl" /><div className="mvp-fr br" />

      {/* ZONE 1 — chat (now spans the old menu + chat columns) */}
      <section className="mvp-chat">
        <div className="mvp-chattop">
          <div className="mvp-wm">J · A · R · V · I · S</div>
          <div className="mvp-chathead">
            <button className="mvp-extend" onClick={() => props.onOpen('activity', 'timeline')} title="Open the timeline">Timeline ↗</button>
          </div>
        </div>
        <div className="mvp-greet">
          <h1>Good {part}, <b>Shay</b>.</h1>
          <div className="mvp-clk">{clock} · {date} · {pending} need you</div>
        </div>
        <div className="mvp-thread">
          {turns.length === 0 ? (
            <div className="mvp-msg j"><div className="who">Jarvis</div>Good {part}, Shay. {pending} things need you, net is ${revenue.toLocaleString()} ({roiX}×). Ask me anything — watch the brain light up as I work.</div>
          ) : turns.map((t) => (
            <div key={t.id}>
              <div className="mvp-msg u"><div className="who">Shay</div>{t.prompt}</div>
              {((t.steps && t.steps.length > 0) || t.loading) && (
                <div className="mvp-steps">
                  {(t.steps || []).map((s, i) => (
                    <div key={i} className="mvp-step">
                      <span className={`mvp-stepk k-${s.kind}`}>{s.kind}</span>
                      <span className="mvp-stepl">{s.label}{s.detail ? <span className="mvp-stepd"> · {s.detail}</span> : null}</span>
                    </div>
                  ))}
                  {t.loading && <div className="mvp-step"><span className="mvp-stepk">···</span><span className="mvp-stepl mvp-think">thinking…</span></div>}
                </div>
              )}
              {(t.resp?.answer || t.commandResult?.message) && (
                <div className="mvp-msg j" style={{ marginTop: 8 }}><div className="who">Jarvis</div>{t.resp?.answer || t.commandResult?.message}</div>
              )}
            </div>
          ))}
        </div>
        <div className="mvp-box">
          <div className="mvp-boxin">
            <button className={`mvp-mic${listening ? ' live' : ''}`} onClick={props.onMic} title={listening ? 'Stop & send' : 'Speak to Jarvis'} aria-label="Voice">
              {listening
                ? <svg viewBox="0 0 24 24" width="17" height="17" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="3" /></svg>
                : <svg viewBox="0 0 24 24" width="19" height="19" fill="currentColor"><rect x="9" y="3" width="6" height="10.5" rx="3" /><path fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21" /></svg>}
            </button>
            <input placeholder="Ask Jarvis, or type a /command…" value={input}
              onChange={(e) => props.onInput(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') props.onSubmit(input) }} />
            <button className="mvp-kbd" onClick={props.onCmdk} title="Command palette">⌘K</button>
          </div>
        </div>
      </section>

      {/* ZONE 3 — stage */}
      <main className="mvp-stage">
        <div className="mvp-top">
          <div className="mvp-providers">
            {(['ollama', 'openai', 'anthropic'] as const).map((p) => (
              <button key={p} className={props.uiProvider === p ? 'on' : ''} onClick={() => props.onProvider(p)}>{p.toUpperCase()}</button>
            ))}
          </div>
          <div className="mvp-topr">
            <button className="mvp-bell" onClick={() => props.onOpen('notifications')} title="Notifications">
              <svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 0 1-3.4 0" /></svg>
              {unread > 0 && <span className="mvp-bellc">{unread}</span>}
            </button>
            <button className={`mvp-tog${overview ? ' on' : ''}`} onClick={() => setOverview((o) => !o)}><span>Windows</span><span className="mvp-sw"><i /></span></button>
          </div>
        </div>

        {!overview && pending > 0 && (
          <button className="mvp-needs" onClick={() => props.onOpen('approvals')}>
            <span className="ico">!</span><span><b>{pending} need you</b><br /><span>action required</span></span>
          </button>
        )}

        {!overview ? (
          <>
            <div className="mvp-brainwrap">
              <svg className="mvp-brain" viewBox="72 46 896 690" preserveAspectRatio="xMidYMid meet" onClick={onSvgClick}>
                <defs>
                  <radialGradient id="mvphub" cx="50%" cy="40%" r="60%"><stop offset="0%" stopColor="#eafff5" /><stop offset="45%" stopColor="#46e0a0" /><stop offset="100%" stopColor="#1f7a52" /></radialGradient>
                  <filter id="mvpglow" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="4" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>
                </defs>

                {/* static brain (memoised) */}
                {baseLayer}

                {/* live highlight overlay — only the active chain re-renders */}
                {graph.edges.map(([a, b], i) => {
                  if (!(chain.has(a) && chain.has(b))) return null
                  const na = nodeById[a], nb = nodeById[b]
                  return <path key={'he' + i} d={curve(na.x, na.y, nb.x, nb.y, 0.13)} className="mvp-edge hot" />
                })}
                {[...chain].map((id) => {
                  const n = nodeById[id]
                  if (n && (n.type === 'agent' || n.type === 'conn' || n.type === 'cap' || n.type === 'intent' || n.type === 'memory' || n.type === 'plugin') && n.hub) {
                    const h = nodeById[n.hub]; return <path key={'hh' + id} d={curve(h.x, h.y, n.x, n.y, 0.13)} className="mvp-edge hot" />
                  }
                  return null
                })}
                {[...chain].map((id) => {
                  const n = nodeById[id]; if (!n) return null
                  const cur = id === current
                  const { isEntity, ly, label } = labelInfo(n)
                  return (
                    <g key={'h' + id} style={{ pointerEvents: 'none' }}>
                      <circle cx={n.x} cy={n.y} r={hotR(n, cur)} className={`mvp-dot t-${n.type} hot`} />
                      <text x={n.x} y={ly} textAnchor="middle" className={`mvp-glabel${isEntity ? ' sm' : ''} hot`}>{label}</text>
                    </g>
                  )
                })}
                {pulsePath && (
                  <circle r="4" className="mvp-pulse">
                    <animateMotion dur="0.9s" repeatCount="indefinite" path={pulsePath} />
                  </circle>
                )}
              </svg>
            </div>
            <div className="mvp-telebox">
              <div className="mvp-tele">
                <span className="k">Status</span><span className="v good">{busy ? 'WORKING' : (snap?.jarvisHealth === 'OK' ? 'OPERATIONAL' : 'ONLINE')}</span>
                <span className="k">CPU</span><span className="v">{cpu}%</span>
                <span className="k">Memory</span><span className="v">{mem}{snap ? ` / ${gb(snap.memory.totalPhysicalBytes)}G` : ''}</span>
                <span className="k">Load</span><span className="v">{snap?.cpu.systemLoadAverage?.toFixed(2) ?? '—'}</span>
                <span className="k">Disk</span><span className="v">{snap ? `${gb(snap.disk.freeBytes)}G free` : '—'}</span>
                <span className="k clk" onClick={() => props.onOpen('capabilities', 'agents')}>Agents</span><span className="v clk" onClick={() => props.onOpen('capabilities', 'agents')}>{agentCount}</span>
                <span className="k clk" onClick={() => props.onOpen('capabilities', 'connectors')}>Connectors</span><span className="v clk" onClick={() => props.onOpen('capabilities', 'connectors')}>{liveConn}/{totalConn} live</span>
                <span className="k clk" onClick={() => props.onOpen('activity', 'runs')}>Jobs</span><span className="v clk" onClick={() => props.onOpen('activity', 'runs')}>{runningJobs} running</span>
                <span className="k">Model</span><span className="v">{model}</span>
                <span className="k clk" onClick={() => props.onOpen('usage', 'revenue')}>Budget</span><span className="v clk" onClick={() => props.onOpen('usage', 'revenue')}>{budLabel}</span>
              </div>
              <div className="mvp-budbar2"><i style={{ width: budPct + '%' }} /></div>
            </div>
          </>
        ) : (
          <div className="mvp-overview">
            <div className="mvp-ovsec">Open windows · {wins.length}</div>
            {wins.length === 0 ? (
              <div className="mvp-ovempty">No windows open — pick one below to open it.</div>
            ) : (
              <div className="mvp-ovgrid">
                {wins.map((w) => (
                  <button key={w.key} className="mvp-ovcard open" onClick={() => props.onFocusWin(w.key)}>
                    <div className="mvp-ovtt">{w.title}<span className="live">{w.minimized ? 'min' : 'open'}</span></div>
                    <div className="mvp-ovbd">{w.subtitle}</div>
                  </button>
                ))}
              </div>
            )}
            <div className="mvp-ovsec">All windows · open one</div>
            <div className="mvp-ovgrid">
              {MENU.flatMap((g) => g.items).map((it) => (
                <button key={'space-' + it.t} className="mvp-ovcard" onClick={() => props.onOpen(it.kind, it.tab)}>
                  <div className="mvp-ovtt"><span className="ic">{it.ic}</span> {it.t}</div>
                  <div className="mvp-ovbd">Open the {it.t} space</div>
                </button>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
