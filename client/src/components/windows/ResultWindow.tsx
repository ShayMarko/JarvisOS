import { isHtmlish, stripHtml } from '../../lib/format'
import { DataView } from '../DataView'
import { ErrorCard } from '../ErrorCard'

export function ResultWindow({ payload }: { payload?: unknown }) {
  const p = payload as { status?: string; message?: string; data?: unknown } | undefined
  if (p?.status === 'ERROR') return <ErrorCard message={p.message} />
  const hasData = p?.data !== undefined && p?.data !== null && p?.data !== ''
  return (<>
    {p?.message && <div className="answer-txt" style={{ marginBottom: hasData ? 16 : 0 }}>{isHtmlish(p.message) ? stripHtml(p.message) : p.message}</div>}
    {hasData ? <DataView value={p!.data} /> : (!p?.message ? <div className="w-empty"><div className="s">Done.</div></div> : null)}
  </>)
}
