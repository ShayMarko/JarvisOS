import type { ReactNode } from 'react'

/**
 * Tiny, dependency-free Markdown renderer for chat answers — handles the subset models actually emit:
 * #/##/### headings, **bold**, *italic*, `code`, [links](url), and -/* and 1. lists. Everything is built
 * as React nodes (no raw HTML injection), so it's safe to render model output.
 */

const INLINE = /(\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\)|\*([^*\n]+)\*)/

function inline(text: string): ReactNode[] {
  const out: ReactNode[] = []
  let rest = text
  let k = 0
  while (rest.length) {
    const m = INLINE.exec(rest)
    if (!m) { out.push(rest); break }
    if (m.index > 0) out.push(rest.slice(0, m.index))
    const tok = m[0]
    if (tok.startsWith('**')) out.push(<strong key={k++}>{m[2]}</strong>)
    else if (tok.startsWith('`')) out.push(<code key={k++} className="md-code">{m[3]}</code>)
    else if (tok.startsWith('[')) out.push(<a key={k++} href={m[5]} target="_blank" rel="noreferrer">{m[4]}</a>)
    else out.push(<em key={k++}>{m[6]}</em>)
    rest = rest.slice(m.index + tok.length)
  }
  return out
}

type Block =
  | { type: 'h'; level: number; text: string }
  | { type: 'p'; text: string }
  | { type: 'list'; ordered: boolean; items: string[] }

export function Markdown({ text, className }: { text: string; className?: string }) {
  const lines = (text || '').replace(/\r/g, '').split('\n')
  const blocks: Block[] = []
  let para: string[] = []
  let list: { ordered: boolean; items: string[] } | null = null
  const flushPara = () => { if (para.length) { blocks.push({ type: 'p', text: para.join(' ') }); para = [] } }
  const flushList = () => { if (list) { blocks.push({ type: 'list', ...list }); list = null } }

  for (const raw of lines) {
    const line = raw.trimEnd()
    if (!line.trim()) { flushPara(); flushList(); continue }
    const h = /^(#{1,6})\s+(.*)$/.exec(line)
    const ol = /^\s*\d+[.)]\s+(.*)$/.exec(line)
    const ul = /^\s*[-*•]\s+(.*)$/.exec(line)
    if (h) { flushPara(); flushList(); blocks.push({ type: 'h', level: h[1].length, text: h[2] }); continue }
    if (ol) { flushPara(); if (!list || !list.ordered) { flushList(); list = { ordered: true, items: [] } } list.items.push(ol[1]); continue }
    if (ul) { flushPara(); if (!list || list.ordered) { flushList(); list = { ordered: false, items: [] } } list.items.push(ul[1]); continue }
    flushList(); para.push(line)
  }
  flushPara(); flushList()

  return (
    <div className={`md${className ? ' ' + className : ''}`}>
      {blocks.map((b, i) => {
        if (b.type === 'h') return <div key={i} className={`md-h md-h${Math.min(b.level, 4)}`}>{inline(b.text)}</div>
        if (b.type === 'list') {
          const items = b.items.map((it, j) => <li key={j}>{inline(it)}</li>)
          return b.ordered ? <ol key={i} className="md-list">{items}</ol> : <ul key={i} className="md-list">{items}</ul>
        }
        return <p key={i} className="md-p">{inline(b.text)}</p>
      })}
    </div>
  )
}
