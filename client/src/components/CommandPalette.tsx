import { useEffect, useState } from 'react'
import type { CommandDefinition } from '../api'

/** ⌘K command palette — fuzzy-filter and run any slash command. */
export function CommandPalette({ commands, onRun, onClose }: { commands: CommandDefinition[]; onRun: (s: string) => void; onClose: () => void }) {
  const [q, setQ] = useState(''); const [sel, setSel] = useState(0)
  const filtered = commands.filter((c) => (c.slash + ' ' + c.description).toLowerCase().includes(q.toLowerCase())).slice(0, 40)
  useEffect(() => setSel(0), [q])
  return (
    <div className="cmdk-overlay" onClick={onClose}>
      <div className="cmdk" onClick={(e) => e.stopPropagation()}>
        <input autoFocus placeholder="Run a command…  /today  /files  /workflows  /policy" value={q}
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
