import { useState } from 'react'
import type { ApprovalRequest } from '../api'
import { ago } from '../lib/format'
import { ApprovalActions } from './ApprovalActions'

const RISK_CLASS: Record<string, string> = { LOW: 'ok', MEDIUM: 'warn', HIGH: 'warn', CRITICAL: 'bad' }

/** Action types that produce a drafted message (email/DM) — Jarvis writes it, you Read & Send (t37). */
function isDraft(a: ApprovalRequest): boolean {
  return /mail|email|gmail|draft|message|reply|dm|slack|discord|telegram|sms|whatsapp|send/i.test(`${a.actionType} ${a.title}`)
}

function when(at: string | null): string {
  if (!at) return '—'
  try { return new Date(at).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) } catch { return '—' }
}

/**
 * Detailed, organised decision card (t116) — everything you need to approve/decline at a glance:
 * risk, action type, when, description and the full preview. For drafted messages (t37) the preview
 * is presented as a readable draft and the primary button reads "Send".
 */
export function ApprovalCard({ a, onDone }: { a: ApprovalRequest; onDone?: () => void }) {
  const draft = isDraft(a)
  const [open, setOpen] = useState(false)
  const previewLabel = draft ? 'Read draft' : 'Preview'

  return (
    <div className="appr-card detailed">
      <div className="appr-head">
        <span className={`risk-badge r-${a.riskLevel.toLowerCase()}`}>{a.riskLevel}</span>
        <span className="appr-title">{a.title}</span>
        <span className="when">{ago(a.createdAt)}</span>
      </div>

      <div className="appr-meta">
        <span className="k">Action</span><span className="v mono">{a.actionType}</span>
        <span className="k">Risk</span><span className="v">{a.riskLevel}</span>
        <span className="k">Requested</span><span className="v">{when(a.createdAt)}</span>
        <span className="k">Status</span><span className="v">{a.status}</span>
        {draft && <><span className="k">Kind</span><span className="v">✉️ Drafted message — review &amp; send</span></>}
      </div>

      {a.description && <div className="appr-desc">{a.description}</div>}

      {a.preview && (
        <div className="appr-draft">
          <button className="appr-readtoggle" onClick={() => setOpen((o) => !o)}>
            {open ? '▾' : '▸'} {previewLabel}
          </button>
          {open && <pre className={`appr-preview${draft ? ' draftbody' : ''}`}>{a.preview}</pre>}
        </div>
      )}

      <ApprovalActions id={a.id} onDone={onDone} approveLabel={draft ? 'Send' : 'Approve'} />
      <span className="appr-risk-hint">{RISK_CLASS[a.riskLevel] === 'bad' ? 'High-risk — review carefully before approving.' : ''}</span>
    </div>
  )
}
