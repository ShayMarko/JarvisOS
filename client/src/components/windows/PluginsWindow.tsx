import { useCallback, useEffect, useState } from 'react'
import { getInstalledPlugins, getPluginCatalog, installPlugin, uninstallPlugin } from '../../api'
import type { PluginCatalogEntry, PluginInfo } from '../../api'
import { friendlyError } from '../../lib/format'

export function PluginsWindow() {
  const [installed, setInstalled] = useState<PluginInfo[] | null>(null)
  const [catalog, setCatalog] = useState<PluginCatalogEntry[] | null>(null)
  const [busy, setBusy] = useState('')
  const [msg, setMsg] = useState('')
  const refresh = useCallback(() => {
    getInstalledPlugins().then(setInstalled).catch(() => setInstalled([]))
    getPluginCatalog().then(setCatalog).catch(() => setCatalog([]))
  }, [])
  useEffect(() => { refresh() }, [refresh])

  const install = (id: string) => {
    setBusy(id); setMsg('')
    installPlugin({ id }).then((added) => { setMsg(`Installed ${added.map(p => p.name).join(', ')}`); refresh() })
      .catch((e) => setMsg(friendlyError((e as Error).message))).finally(() => setBusy(''))
  }
  const remove = (id: string, name: string) => {
    if (!confirm(`Uninstall "${name}"? Its tools will be removed from Jarvis.`)) return
    setBusy(id); setMsg('')
    uninstallPlugin(id).then((r) => { setMsg(r.message); refresh() })
      .catch((e) => setMsg(friendlyError((e as Error).message))).finally(() => setBusy(''))
  }
  const notInstalled = (catalog ?? []).filter((c) => !c.installed)

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        {msg && <span className="files-msg">{msg}</span>}
      </div>
      <div className="dv-sec-title">Installed</div>
      {!installed ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : installed.length === 0 ? <div className="w-empty"><div className="s">No plugins installed. Install one from the marketplace below.</div></div>
        : <div className="rows">{installed.map((p) => (
            <div className="row" key={p.id}>
              <span className="grow"><strong>{p.name}</strong> <span style={{ color: 'var(--muted)' }}>v{p.version}</span>
                <div style={{ color: 'var(--muted)', fontSize: 12 }}>{p.description}</div>
                <div style={{ fontSize: 11, marginTop: 2 }}>{p.tools.map((t) => <span key={t} className="pill low" style={{ marginRight: 4 }}>{t}</span>)}</div></span>
              <button className="hint" disabled={busy === p.id} onClick={() => remove(p.id, p.name)}>{busy === p.id ? '…' : 'Uninstall'}</button>
            </div>))}</div>}

      <div className="dv-sec-title" style={{ marginTop: 16 }}>Marketplace</div>
      {!catalog ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : notInstalled.length === 0 ? <div className="w-empty"><div className="s">{(catalog.length ?? 0) === 0 ? 'No catalog configured (add a catalog.json in the Plugins folder).' : 'Everything in the catalog is installed.'}</div></div>
        : <div className="rows">{notInstalled.map((c) => (
            <div className="row" key={c.id}>
              <span className="grow"><strong>{c.name}</strong> <span style={{ color: 'var(--muted)' }}>v{c.version}</span>
                <div style={{ color: 'var(--muted)', fontSize: 12 }}>{c.description}</div></span>
              <button className="hint" disabled={busy === c.id} onClick={() => install(c.id)}>{busy === c.id ? '…' : 'Install'}</button>
            </div>))}</div>}
    </>
  )
}
