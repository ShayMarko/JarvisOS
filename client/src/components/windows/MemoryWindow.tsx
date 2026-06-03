import { useCallback, useEffect, useState } from 'react'
import { createMemory, deleteMemory, getMemoryList } from '../../api'
import type { Memory } from '../../api'

export function MemoryWindow() {
  const [items, setItems] = useState<Memory[] | null>(null)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const load = useCallback(() => getMemoryList().then(setItems).catch(() => setItems([])), [])
  useEffect(() => { load() }, [load])
  const add = () => {
    if (!content.trim()) return
    createMemory({ category: 'fact', title: title.trim() || 'Note', content: content.trim(), source: 'manual' })
      .then(() => { setTitle(''); setContent(''); load() }).catch(() => {})
  }
  const del = (id: string) => deleteMemory(id).then(load).catch(() => {})
  return (
    <>
      <div className="w-head-row"><span className="w-section-title">Trusted memory · {items?.length ?? 0}</span>
        <button className="btn-soft" onClick={load}>⟳ Refresh</button></div>
      <div className="mem-add">
        <input placeholder="Title (optional)" value={title} onChange={(e) => setTitle(e.target.value)} style={{ flex: '0 0 150px' }} />
        <input placeholder="Tell Jarvis a fact to remember…" value={content} onChange={(e) => setContent(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') add() }} style={{ flex: 1 }} />
        <button className="btn-soft" onClick={add} disabled={!content.trim()}>+ Remember</button>
      </div>
      {!items ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : items.length === 0 ? <div className="w-empty"><div className="big">◈</div><div className="s">No memory yet. Add a fact above, or just tell Jarvis “remember that …” in the command bar.</div></div>
        : <div className="rows">{items.map((m) => (
            <div className="row" key={m.id}>
              <span className="grow"><strong>{m.title}</strong> — <span style={{ color: 'var(--muted)' }}>{m.content}</span></span>
              <span className="pill low">{m.category}</span>
              <button className="row-del" title="Forget" onClick={() => del(m.id)}>✕</button>
            </div>
          ))}</div>}
    </>
  )
}
