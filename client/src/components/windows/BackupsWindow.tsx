import { useState } from 'react'
import { createBackup, listBackups, restoreBackup } from '../../api'
import { friendlyError, kb } from '../../lib/format'
import { useFetch } from '../../lib/useFetch'

export function BackupsWindow() {
  const { data: items, refresh } = useFetch(listBackups, [])
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')

  const make = () => {
    setBusy(true); setMsg('')
    createBackup().then(() => { setMsg('Snapshot created.'); refresh() })
      .catch((e) => setMsg(friendlyError((e as Error).message))).finally(() => setBusy(false))
  }
  const restore = (name: string) => {
    if (!confirm(`Restore "${name}"? This overwrites current files in the Explorer with the snapshot's versions.`)) return
    setBusy(true); setMsg('')
    restoreBackup(name).then((r) => setMsg(r.message))
      .catch((e) => setMsg(friendlyError((e as Error).message))).finally(() => setBusy(false))
  }

  return (
    <>
      <div className="files-bar">
        <button className="hint" disabled={busy} onClick={make}>{busy ? '…' : '+ New snapshot'}</button>
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        {msg && <span className="files-msg">{msg}</span>}
      </div>
      {!items ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : items.length === 0 ? <div className="w-empty"><div className="big">🗄</div><div className="s">No snapshots yet. Create one to protect your Explorer.</div></div>
        : <div className="rows">{items.map((b) => (
            <div className="row" key={b.name}>
              <span className="grow"><strong>{b.name}</strong> <span style={{ color: 'var(--muted)' }}>· {kb(b.sizeBytes)}</span>
                <div style={{ color: 'var(--muted)', fontSize: 12 }}>{new Date(b.createdAt).toLocaleString()}</div></span>
              <button className="hint" disabled={busy} onClick={() => restore(b.name)}>Restore</button>
            </div>))}</div>}
    </>
  )
}
