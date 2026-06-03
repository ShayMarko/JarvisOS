/** Humanize a JSON key: camelCase / snake-case / kebab → "Title Case". */
function humanize(key: string): string {
  return key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

/** Recursively renders any value (object / array / scalar) as a clean HUD view. */
export function DataView({ value }: { value: unknown }): React.ReactElement {
  if (value === null || value === undefined || value === '') return <span className="dv-empty">—</span>
  if (typeof value === 'string') return value.includes('\n') ? <pre className="raw">{value}</pre> : <span className="dv-scalar">{value}</span>
  if (typeof value === 'number') return <span className="dv-num">{value.toLocaleString()}</span>
  if (typeof value === 'boolean') return <span className={`dv-bool ${value ? 't' : 'f'}`}>{value ? 'yes' : 'no'}</span>
  if (Array.isArray(value)) {
    if (value.length === 0) return <span className="dv-empty">none</span>
    const allScalar = value.every((v) => v === null || typeof v !== 'object')
    if (allScalar) return <div className="dv-list">{value.map((v, i) => <div className="dv-li" key={i}>{String(v)}</div>)}</div>
    return <div>{value.map((v, i) => <div className="dv-card" key={i}><DataView value={v} /></div>)}</div>
  }
  const entries = Object.entries(value as Record<string, unknown>)
  if (entries.length === 0) return <span className="dv-empty">empty</span>
  return (
    <div className="dv">
      {entries.map(([k, v]) => {
        const nested = v !== null && typeof v === 'object' && (Array.isArray(v) ? v.some((x) => x && typeof x === 'object') : true)
        if (nested) return (
          <div className="dv-section" key={k}>
            <div className="dv-sec-title">{humanize(k)}</div>
            <div className="dv-sec-body"><DataView value={v} /></div>
          </div>
        )
        return <div className="dv-row" key={k}><span className="dv-key">{humanize(k)}</span><span className="dv-val"><DataView value={v} /></span></div>
      })}
    </div>
  )
}
