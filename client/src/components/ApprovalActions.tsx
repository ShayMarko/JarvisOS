import { useState } from 'react'
import { approveRequest, denyRequest } from '../api'

/**
 * Inline Approve / Decline buttons for a pending approval — reused under approval notifications
 * (bell popover + Notifications window) and in the Approval Center. Calls the approval REST endpoints
 * and reports the decision back via onDone so the host can refresh.
 */
export function ApprovalActions({ id, onDone }: { id: string; onDone?: (decision: 'approved' | 'denied') => void }) {
  const [busy, setBusy] = useState<null | 'approve' | 'deny'>(null)
  const [remember, setRemember] = useState(false)
  const [done, setDone] = useState<null | string>(null)
  const [err, setErr] = useState<string | null>(null)

  const decide = (kind: 'approve' | 'deny') => {
    setBusy(kind); setErr(null)
    const call = kind === 'approve' ? approveRequest(id, remember) : denyRequest(id, remember)
    call
      .then(() => { setDone(kind === 'approve' ? 'Approved ✓' : 'Declined'); onDone?.(kind === 'approve' ? 'approved' : 'denied') })
      .catch((e) => setErr((e as Error).message))
      .finally(() => setBusy(null))
  }

  if (done) return <div className="appr-done">{done}</div>

  return (
    <div className="appr-actions" onClick={(e) => e.stopPropagation()}>
      <button className="appr-btn ok" disabled={busy !== null} onClick={() => decide('approve')}>
        {busy === 'approve' ? '…' : 'Approve'}
      </button>
      <button className="appr-btn no" disabled={busy !== null} onClick={() => decide('deny')}>
        {busy === 'deny' ? '…' : 'Decline'}
      </button>
      <label className="appr-remember" title="Remember this decision for the same kind of action">
        <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} /> remember
      </label>
      {err && <span className="appr-err">{err}</span>}
    </div>
  )
}
