import { useEffect, useState } from 'react'
import { getAgents } from '../../api'
import type { AgentDef } from '../../api'

/** Human label + display order for each agent category (the rest fall through alphabetically). */
const CATEGORY_LABELS: Record<string, string> = {
  general: 'General', dev: 'Development', research: 'Research & Knowledge',
  data: 'Data & Analysis', files: 'Files', connectors: 'Connectors',
  monitoring: 'System & Monitoring', security: 'Security', memory: 'Memory',
  workflows: 'Workflows & Automation', temporary: 'Temporary',
}
const CATEGORY_ORDER = ['general', 'dev', 'research', 'data', 'files', 'connectors', 'monitoring', 'security', 'memory', 'workflows', 'temporary']

function agentRow(a: AgentDef) {
  return (
    <div className="row" key={a.slug}>
      <span className="grow"><strong>{a.name}</strong> — <span style={{ color: 'var(--muted)' }}>{a.role}</span></span>
    </div>
  )
}

export function AgentsWindow() {
  const [agents, setAgents] = useState<AgentDef[] | null>(null)
  const [open, setOpen] = useState<Record<string, boolean>>({})   // category → expanded; default CLOSED
  const [q, setQ] = useState('')
  useEffect(() => { getAgents().then(setAgents).catch(() => setAgents([])) }, [])
  if (!agents) return <div className="w-empty"><span className="spin-fast">◠</span></div>

  // Group by category, then order known categories first, unknown ones alphabetically after.
  const groups: Record<string, AgentDef[]> = {}
  for (const a of agents) (groups[a.category] ||= []).push(a)
  const cats = Object.keys(groups).sort((a, b) => {
    const ia = CATEGORY_ORDER.indexOf(a), ib = CATEGORY_ORDER.indexOf(b)
    if (ia !== -1 || ib !== -1) return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib)
    return a.localeCompare(b)
  })

  const query = q.trim().toLowerCase()
  // Search overrides collapse: match across all agents (name/role/category) regardless of toggle state.
  const matches = query
    ? agents.filter((a) => `${a.name} ${a.role} ${a.category}`.toLowerCase().includes(query))
    : []

  return (
    <div className="files-pane">
      <div className="agents-head">
        <span className="grow"><strong>{agents.length}</strong> agents</span>
        <span className="s">{cats.length} categories</span>
      </div>
      <div className="w-search">🔍 <input placeholder="Search agents by name, role or category…" value={q}
        onChange={(e) => setQ(e.target.value)} /></div>

      {query ? (
        matches.length === 0
          ? <div className="w-empty"><div className="s">No agents match “{q}”.</div></div>
          : <div className="rows">{matches.map(agentRow)}</div>
      ) : (
        cats.map((cat) => {
          const items = groups[cat]
          const isOpen = !!open[cat]   // categories start collapsed
          return (
            <div key={cat}>
              <button className="files-group" onClick={() => setOpen((o) => ({ ...o, [cat]: !o[cat] }))}>
                <span className="chev">{isOpen ? '▾' : '▸'}</span>{CATEGORY_LABELS[cat] || cat}<span className="cnt">{items.length}</span>
              </button>
              {isOpen && items.map(agentRow)}
            </div>
          )
        })
      )}
    </div>
  )
}
