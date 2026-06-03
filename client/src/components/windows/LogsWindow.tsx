import { useEffect, useState } from 'react'
import { getAudit } from '../../api'
import type { AuditEntry } from '../../api'
import { ago } from '../../lib/format'

export function LogsWindow() {
  const [logs, setLogs] = useState<AuditEntry[] | null>(null)
  useEffect(() => { getAudit(60).then(setLogs).catch(() => setLogs([])) }, [])
  return !logs ? <div className="w-empty"><span className="spin-fast">◠</span></div>
    : logs.length === 0 ? <div className="w-empty"><div className="s">No activity logged yet.</div></div>
    : <div className="rows">{logs.map((l) => (
        <div className="row" key={l.id}><span className={`dot-s ${l.status === 'OK' ? 'ok' : l.status === 'ERROR' ? 'bad' : 'warn'}`} />
          <span className="grow">{l.command || l.inputType}{l.input ? ` — ${l.input}` : ''}</span><span className="when">{ago(l.timestamp)}</span></div>
      ))}</div>
}
