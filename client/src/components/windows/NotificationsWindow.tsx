import { useCallback, useEffect, useState } from 'react'
import { getApprovals, getNotifications } from '../../api'
import type { ApprovalRequest, NotificationItem } from '../../api'
import { ago } from '../../lib/format'
import { ApprovalCard } from '../ApprovalCard'

export function NotificationsWindow() {
  const [items, setItems] = useState<NotificationItem[] | null>(null)
  const [approvals, setApprovals] = useState<Record<string, ApprovalRequest>>({})
  const refresh = useCallback(() => {
    getNotifications().then(setItems).catch(() => setItems([]))
    // Pull the live approval requests so an approval notification can render the full
    // organised decision card (t116) and drafted-message Read/Send controls (t37).
    getApprovals()
      .then((list) => setApprovals(Object.fromEntries(list.map((a) => [a.id, a]))))
      .catch(() => setApprovals({}))
  }, [])
  useEffect(() => { refresh() }, [refresh])

  return !items ? <div className="w-empty"><span className="spin-fast">◠</span></div>
    : items.length === 0 ? <div className="w-empty"><div className="big">🔔</div><div className="s">Nothing yet — you’re all caught up.</div></div>
    : <div className="rows">{items.map((n) => {
        const appr = n.source === 'approval' && n.actionId ? approvals[n.actionId] : undefined
        // Pending approval → rich, organised decision card with Read draft / Send (or Approve) + Decline.
        if (appr && appr.status === 'PENDING') {
          return <ApprovalCard key={n.id} a={appr} onDone={refresh} />
        }
        return (
          <div className="row" key={n.id} style={{ alignItems: 'flex-start', flexWrap: 'wrap' }}>
            <span className={`dot-s ${n.type === 'error' ? 'bad' : n.type === 'warning' ? 'warn' : 'ok'}`} style={{ marginTop: 5 }} />
            <span className="grow" style={{ whiteSpace: 'normal' }}><strong>{n.title}</strong>{n.risk ? <span className={`risk-badge r-${n.risk.toLowerCase()}`}>{n.risk}</span> : null}{n.body ? <div style={{ color: 'var(--muted)', fontSize: 12 }}>{n.body}</div> : null}</span>
            <span className="when">{ago(n.createdAt)}</span>
          </div>
        )
      })}</div>
}
