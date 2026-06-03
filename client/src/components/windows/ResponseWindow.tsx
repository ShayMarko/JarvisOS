import type { ChatResponse, Step } from '../../api'
import { isHtmlish, stripHtml } from '../../lib/format'
import { DataView } from '../DataView'
import { ErrorCard } from '../ErrorCard'

export function ResponseWindow({ payload }: { payload?: unknown }) {
  const p = payload as { loading?: boolean; steps?: Step[]; resp?: ChatResponse; commandResult?: { status?: string; message?: string; data?: unknown } } | undefined
  const steps = p?.steps ?? []
  const r = p?.resp
  const cr = p?.commandResult
  const hasCrData = cr?.data !== undefined && cr?.data !== null && cr?.data !== ''
  return (
    <>
      {steps.length > 0 && (
        <div className="substeps" style={{ marginLeft: 0, paddingLeft: 0, marginBottom: 12 }}>
          {steps.map((s, i) => (
            <div className="substep" key={i}><span className="kind">{s.kind}</span><span className="lbl">{s.label}{s.detail ? <span className="det"> — {s.detail}</span> : null}</span></div>
          ))}
          {p?.loading && <div className="substep"><span className="kind"><span className="spin-fast">◠</span></span><span className="det">working…</span></div>}
        </div>
      )}
      {!r && !cr && p?.loading && steps.length === 0 && <div className="w-empty"><span className="spin-fast">◠</span><div className="s">Jarvis is thinking…</div></div>}
      {r && (
        <>
          <div className="w-section-title" style={{ margin: '4px 0 8px' }}>{r.agent} agent</div>
          {isHtmlish(r.answer) ? <ErrorCard message={r.answer} /> : <div className="answer-txt">{r.answer}</div>}
          <div className="answer-meta"><span>model {r.model}</span><span>{r.tokens} tokens</span>{r.steps.length > 0 && <span>{r.steps.length} steps</span>}</div>
        </>
      )}
      {!r && cr && (cr.status === 'ERROR'
        ? <ErrorCard message={cr.message} />
        : <>
            {cr.message && <div className="answer-txt" style={{ marginBottom: hasCrData ? 14 : 0 }}>{isHtmlish(cr.message) ? stripHtml(cr.message) : cr.message}</div>}
            {hasCrData ? <DataView value={cr.data} /> : null}
          </>)}
    </>
  )
}
