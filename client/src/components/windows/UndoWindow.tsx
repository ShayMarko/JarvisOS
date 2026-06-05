import { useState } from 'react'
import { getUndo, undoLast } from '../../api'
import { useFetch } from '../../lib/useFetch'

export function UndoWindow() {
  const { data: state, setData: setState, refresh } = useFetch(getUndo, { count: 0, recent: [] })
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<string | null>(null)

  const undo = () => {
    setBusy(true); setMsg(null)
    undoLast()
      .then((r) => { setMsg(r.result); setState((s) => ({ count: r.count, recent: s?.recent.slice(1) ?? [] })) })
      .catch((e) => setMsg((e as Error).message))
      .finally(() => { setBusy(false); refresh() })
  }

  if (!state) return <div className="w-empty"><span className="spin-fast">◠</span></div>

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        <button className="hint" disabled={busy || state.count === 0} onClick={undo}>↩ Undo last</button>
      </div>
      {msg && <div className="appr-desc" style={{ padding: '0 4px 8px' }}>{msg}</div>}
      {state.count === 0
        ? <div className="w-empty"><div className="big">↩</div><div className="s">Nothing to undo right now.</div></div>
        : <div className="rows">
            {state.recent.map((d, i) => (
              <div className="row" key={i} style={{ alignItems: 'flex-start' }}>
                <span className="dot-s ok" style={{ marginTop: 5 }} />
                <span className="grow" style={{ whiteSpace: 'normal' }}>{i === 0 ? <strong>{d}</strong> : d}{i === 0 && <span className="dim" style={{ fontSize: 11 }}> · next to undo</span>}</span>
              </div>))}
          </div>}
    </>
  )
}
