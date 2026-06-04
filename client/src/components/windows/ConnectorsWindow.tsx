import { useCallback, useEffect, useState } from 'react'
import { createSecret, deleteSecret, getConnectors, getSecrets } from '../../api'
import type { ConnectorInfo, SecretView } from '../../api'

const DOT: Record<string, string> = { CONNECTED: 'ok', DISCONNECTED: 'warn', ERROR: 'bad' }

export function ConnectorsWindow() {
  const [conns, setConns] = useState<ConnectorInfo[] | null>(null)
  const [secrets, setSecrets] = useState<SecretView[]>([])
  const [draft, setDraft] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState<string | null>(null)

  const refresh = useCallback(() => {
    getConnectors().then(setConns).catch(() => setConns([]))
    getSecrets().then(setSecrets).catch(() => {})
  }, [])
  useEffect(() => { refresh() }, [refresh])

  const secretFor = (name: string | null) => (name ? secrets.find((s) => s.name === name) : undefined)

  const save = (c: ConnectorInfo) => {
    const value = (draft[c.id] || '').trim()
    if (!c.requiredSecret || !value) return
    setBusy(c.id)
    createSecret({ name: c.requiredSecret, connector: c.id, value })
      .then(() => { setDraft((d) => ({ ...d, [c.id]: '' })); refresh() })
      .finally(() => setBusy(null))
  }

  const clear = (s: SecretView) => { setBusy(s.id); deleteSecret(s.id).then(refresh).finally(() => setBusy(null)) }

  if (!conns) return <div className="w-empty"><span className="spin-fast">◠</span></div>

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        <span className="note">{conns.filter((c) => c.status === 'CONNECTED').length}/{conns.length} connected</span>
      </div>
      <div className="rows">
        {conns.map((c) => {
          const sec = secretFor(c.requiredSecret)
          return (
            <div className="appr-card" key={c.id}>
              <div className="appr-head">
                <span className={`dot-s ${DOT[c.status] ?? 'warn'}`} />
                <span className="appr-title">{c.name} <span className="dim" style={{ fontSize: 11 }}>· {c.category}</span></span>
                <span className="when">{c.status.toLowerCase()}</span>
              </div>
              {c.requiredSecret ? (
                sec ? (
                  <div className="appr-actions">
                    <span className="dim" style={{ fontSize: 12 }}>🔑 {c.requiredSecret}: {sec.masked}</span>
                    <button className="appr-btn no" disabled={busy === sec.id} onClick={() => clear(sec)}>Remove</button>
                  </div>
                ) : (
                  <div className="rev-form">
                    <input className="rev-in grow" type="password" placeholder={`Paste ${c.requiredSecret}…`}
                      value={draft[c.id] || ''} onChange={(e) => setDraft((d) => ({ ...d, [c.id]: e.target.value }))}
                      onKeyDown={(e) => { if (e.key === 'Enter') save(c) }} />
                    <button className="hint" disabled={busy === c.id} onClick={() => save(c)}>{busy === c.id ? '…' : 'Connect'}</button>
                  </div>
                )
              ) : <div className="appr-desc">No credential required.</div>}
              {c.actions.length > 0 && (
                <div className="appr-desc">Actions: {c.actions.map((a) => a.id).join(', ')}</div>
              )}
            </div>
          )
        })}
      </div>
    </>
  )
}
