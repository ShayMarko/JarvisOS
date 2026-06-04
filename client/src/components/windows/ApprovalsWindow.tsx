import { useCallback, useEffect, useState } from 'react'
import { getApprovals } from '../../api'
import type { ApprovalRequest } from '../../api'
import { ago } from '../../lib/format'
import { ApprovalActions } from '../ApprovalActions'

const RISK_CLASS: Record<string, string> = { LOW: 'ok', MEDIUM: 'warn', HIGH: 'warn', CRITICAL: 'bad' }

export function ApprovalsWindow() {
  const [items, setItems] = useState<ApprovalRequest[] | null>(null)
  const refresh = useCallback(() => { getApprovals().then(setItems).catch(() => setItems([])) }, [])
  useEffect(() => { refresh() }, [refresh])

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
            {items.map((a) => (
              <div className="appr-card" key={a.id}>
                <div className="appr-head">
                  <span className={`risk ${RISK_CLASS[a.riskLevel] ?? 'warn'}`}>{a.riskLevel}</span>
                  <span className="appr-title">{a.title}</span>
                  <span className="when">{ago(a.createdAt)}</span>
                </div>
                {a.description && <div className="appr-desc">{a.description}</div>}
                {a.preview && <pre className="appr-preview">{a.preview}</pre>}
                <ApprovalActions id={a.id} onDone={refresh} />
              </div>
            ))}
          </div>}
    </>
  )
}
