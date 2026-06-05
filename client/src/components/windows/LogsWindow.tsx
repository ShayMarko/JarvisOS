import { getAudit } from '../../api'
import { ago } from '../../lib/format'
import { useFetch } from '../../lib/useFetch'

export function LogsWindow() {
  const { data: logs } = useFetch(() => getAudit(60), [])
  return !logs ? <div className="w-empty"><span className="spin-fast">◠</span></div>
    : logs.length === 0 ? <div className="w-empty"><div className="s">No activity logged yet.</div></div>
    : <div className="rows">{logs.map((l) => (
        <div className="row" key={l.id}><span className={`dot-s ${l.status === 'OK' ? 'ok' : l.status === 'ERROR' ? 'bad' : 'warn'}`} />
          <span className="grow">{l.command || l.inputType}{l.input ? ` — ${l.input}` : ''}</span><span className="when">{ago(l.timestamp)}</span></div>
      ))}</div>
}
