import { useCallback, useEffect, useState } from 'react'
import { getFileContent, listFiles, revealFile, writeFile } from '../../api'
import type { FileNode } from '../../api'
import { fileKind, friendlyError, kb, rawUrl } from '../../lib/format'

export function FilesWindow() {
  const [cwd, setCwd] = useState('')
  const [entries, setEntries] = useState<FileNode[] | null>(null)
  const [sel, setSel] = useState<FileNode | null>(null)
  const [content, setContent] = useState('')
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({})

  const load = useCallback((path: string) => {
    setEntries(null); setMsg('')
    listFiles(path).then(setEntries).catch((e) => { setEntries([]); setMsg(friendlyError((e as Error).message)) })
  }, [])
  useEffect(() => { load(cwd) }, [cwd, load])

  const isAbs = cwd.startsWith('/')
  const segs = cwd.split('/').filter(Boolean)
  const goUp = () => { const i = cwd.lastIndexOf('/'); setSel(null); setCwd(i <= 0 ? '' : cwd.slice(0, i)) }
  const crumbTo = (i: number) => { const j = segs.slice(0, i + 1).join('/'); return isAbs ? '/' + j : j }

  const open = (n: FileNode) => {
    if (n.directory) { setSel(null); setCwd(n.path); return }
    setSel(n); setMsg('')
    if (fileKind(n.name) !== 'text') { setContent(''); return }   // image/video/audio/pdf/binary → /raw or fallback
    setBusy(true)
    getFileContent(n.path)
      .then((f) => { setContent(f.content); setDirty(false) })
      .catch((er) => setMsg(friendlyError((er as Error).message)))
      .finally(() => setBusy(false))
  }
  const save = () => {
    if (!sel) return
    setBusy(true)
    writeFile(sel.path, content)
      .then(() => { setDirty(false); setMsg('Saved.'); setTimeout(() => setMsg(''), 1800) })
      .catch((e) => setMsg(friendlyError((e as Error).message)))
      .finally(() => setBusy(false))
  }
  const openLocation = () => { if (sel) revealFile(sel.path).catch(() => setMsg('Could not reveal the file.')) }

  const kind = sel ? fileKind(sel.name) : 'text'

  return (
    <div className="files">
      <div className="files-bar">
        <button className="hint" disabled={!cwd} onClick={goUp}>↑ Up</button>
        <span className="crumbs"><button className="crumb" onClick={() => { setSel(null); setCwd('') }}>Explorer</button>
          {segs.map((c, i) => <button key={i} className="crumb" onClick={() => { setSel(null); setCwd(crumbTo(i)) }}>/ {c}</button>)}</span>
        <span className="grow" />
        {msg && <span className="files-msg">{msg}</span>}
      </div>
      <div className="files-split">
        <div className="files-list">
          {!entries ? <div className="w-empty"><span className="spin-fast">◠</span></div>
            : entries.length === 0 ? <div className="w-empty"><div className="s">Empty folder.</div></div>
            : cwd === '' ? (() => {
                const fileRow = (n: FileNode) => (
                  <button key={n.path} className={`file-row${sel?.path === n.path ? ' sel' : ''}`} onClick={() => open(n)}>
                    <span className="fic">{n.directory ? '📁' : '📄'}</span><span className="fname">{n.name}</span>
                    <span className="fsize">{n.directory ? '' : kb(n.size)}</span></button>)
                const section = (title: string, items: FileNode[]) => (
                  <div key={title}>
                    <button className="files-group" onClick={() => setCollapsed((c) => ({ ...c, [title]: !c[title] }))}>
                      <span className="chev">{collapsed[title] ? '▸' : '▾'}</span>{title}<span className="cnt">{items.length}</span>
                    </button>
                    {!collapsed[title] && items.map(fileRow)}
                  </div>)
                const drive = entries.filter((n) => !n.path.startsWith('/'))   // jarvis_drive (relative)
                const os = entries.filter((n) => n.path.startsWith('/'))       // OS mounts (absolute)
                return <>
                  {section('jarvis_drive', drive)}
                  {os.length > 0 && section('OS', os)}
                </>
              })()
            : entries.map((n) => (
              <button key={n.path} className={`file-row${sel?.path === n.path ? ' sel' : ''}`} onClick={() => open(n)}>
                <span className="fic">{n.directory ? '📁' : '📄'}</span>
                <span className="fname">{n.name}</span>
                <span className="fsize">{n.directory ? '' : kb(n.size)}</span>
              </button>))}
        </div>
        <div className="files-view">
          {!sel ? <div className="w-empty"><div className="s">Select a file to view or edit it.</div></div>
            : <>
              <div className="files-vhead"><strong>{sel.name}</strong>
                <span className="grow" />
                <button className="hint" onClick={openLocation} title="Reveal in Finder">Open location</button>
                {kind === 'text' && <button className="hint" disabled={busy || !dirty} onClick={save}>{busy ? '…' : dirty ? 'Save' : 'Saved'}</button>}</div>
              {kind === 'image' ? <div className="files-preview"><img src={rawUrl(sel.path)} alt={sel.name} /></div>
                : kind === 'video' ? <div className="files-preview"><video src={rawUrl(sel.path)} controls /></div>
                : kind === 'audio' ? <div className="files-preview"><audio src={rawUrl(sel.path)} controls /></div>
                : kind === 'pdf' ? <iframe className="files-preview" src={rawUrl(sel.path)} title={sel.name} />
                : kind === 'binary' ? <div className="w-empty"><div className="big">📦</div><div className="s">No inline preview for this file type.<br />Use “Open location” to open it in Finder.</div></div>
                : busy ? <div className="w-empty"><span className="spin-fast">◠</span></div>
                : <textarea className="files-edit" value={content} spellCheck={false}
                    onChange={(e) => { setContent(e.target.value); setDirty(true) }} />}
            </>}
        </div>
      </div>
    </div>
  )
}
