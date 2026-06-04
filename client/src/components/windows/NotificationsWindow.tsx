import { useCallback, useEffect, useState } from 'react'
import { getNotifications } from '../../api'
import type { NotificationItem } from '../../api'
import { ago } from '../../lib/format'
import { ApprovalActions } from '../ApprovalActions'

export function NotificationsWindow() {
  const [items, setItems] = useState<NotificationItem[] | null>(null)
  const refresh = useCallback(() => { getNotifications().then(setItems).catch(() => setItems([])) }, [])
  useEffect(() => { refresh() }, [refresh])
  return !items ? <div className="w-empty"><span className="spin-fast">◠</span></div>
    : items.length === 0 ? <div className="w-empty"><div className="big">🔔</div><div className="s">Nothing yet — you’re all caught up.</div></div>
    : <div className="rows">{items.map((n) => (
        <div className="row" key={n.id} style={{ alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <span className={`dot-s ${n.type === 'error' ? 'bad' : n.type === 'warning' ? 'warn' : 'ok'}`} style={{ marginTop: 5 }} />
          <span className="grow" style={{ whiteSpace: 'normal' }}><strong>{n.title}</strong>{n.body ? <div style={{ color: 'var(--muted)', fontSize: 12 }}>{n.body}</div> : null}</span>
          <span className="when">{ago(n.createdAt)}</span>
          {n.source === 'approval' && n.actionId && (
            <span style={{ flexBasis: '100%' }}><ApprovalActions id={n.actionId} onDone={refresh} /></span>
          )}
        </div>))}</div>
}
