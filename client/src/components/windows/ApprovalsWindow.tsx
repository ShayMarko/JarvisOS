import { getApprovals } from '../../api'
import { useFetch } from '../../lib/useFetch'
import { ApprovalCard } from '../ApprovalCard'

export function ApprovalsWindow() {
  const { data: items, refresh } = useFetch(getApprovals, [])

  if (!items) return <div className="w-empty"><span className="spin-fast">◠</span></div>

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        <span className="note">{items.length} awaiting your decision</span>
      </div>
      {items.length === 0
        ? <div className="w-empty"><div className="big">✓</div><div className="s">Nothing waiting — Jarvis is clear to act.</div></div>
        : <div className="rows">
            {items.map((a) => <ApprovalCard key={a.id} a={a} onDone={refresh} />)}
          </div>}
    </>
  )
}
