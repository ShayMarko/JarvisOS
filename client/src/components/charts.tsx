/** Small SVG viz primitives shared by the Tokens dashboard. */

export const CHART_COLORS = ['#45d6ff', '#3ad29f', '#ffb454', '#ff6b81', '#b58cff', '#7bd0ff', '#8effc1']

export function Donut({ data }: { data: { label: string; value: number }[] }) {
  const total = data.reduce((s, d) => s + d.value, 0) || 1
  const r = 52, circ = 2 * Math.PI * r
  let off = 0
  return (
    <svg viewBox="0 0 130 130" width={130} height={130} style={{ flex: 'none' }}>
      <g transform="translate(65,65) rotate(-90)">
        <circle r={r} fill="none" stroke="rgba(120,150,190,0.12)" strokeWidth={16} />
        {data.map((d, i) => {
          const len = (d.value / total) * circ
          const seg = <circle key={i} r={r} fill="none" stroke={CHART_COLORS[i % CHART_COLORS.length]}
            strokeWidth={16} strokeDasharray={`${len} ${circ - len}`} strokeDashoffset={-off} />
          off += len
          return seg
        })}
      </g>
    </svg>
  )
}

export function Spark({ points }: { points: number[] }) {
  if (points.length < 2) return <div className="note">Not enough runs yet for a trend.</div>
  const w = 360, h = 56, max = Math.max(...points, 1), step = w / (points.length - 1)
  const d = points.map((p, i) => `${i ? 'L' : 'M'}${(i * step).toFixed(1)},${(h - (p / max) * h).toFixed(1)}`).join(' ')
  const area = `${d} L${w},${h} L0,${h} Z`
  return (
    <svg viewBox={`0 0 ${w} ${h}`} width="100%" height={h} preserveAspectRatio="none">
      <path d={area} fill="rgba(69,214,255,0.10)" />
      <path d={d} fill="none" stroke="var(--accent)" strokeWidth={1.5} />
    </svg>
  )
}

/**
 * Gross → Net waterfall (t51). Walks from a starting value through +/− deltas to a final total,
 * each step a floating bar so you can read how gross becomes net at a glance.
 */
export function Waterfall({ items, fmt = (n) => n.toLocaleString() }: {
  items: { label: string; delta?: number; total?: boolean }[]
  fmt?: (n: number) => string
}) {
  let run = 0
  const rows = items.map((it) => {
    if (it.total) return { label: it.label, from: 0, to: run, delta: run, total: true }
    const from = run; run += it.delta || 0
    return { label: it.label, from, to: run, delta: it.delta || 0, total: false }
  })
  const lo = Math.min(0, ...rows.map((r) => Math.min(r.from, r.to)))
  const hi = Math.max(0, ...rows.map((r) => Math.max(r.from, r.to)))
  const span = hi - lo || 1
  const xp = (v: number) => ((v - lo) / span) * 100
  return (
    <div className="wfall">
      {rows.map((r, i) => {
        const a = Math.min(r.from, r.to), b = Math.max(r.from, r.to)
        const cls = r.total ? 'total' : r.delta >= 0 ? 'pos' : 'neg'
        return (
          <div className="wf-row" key={i}>
            <span className="wf-lbl">{r.label}</span>
            <span className="wf-track">
              <span className={`wf-bar ${cls}`} style={{ left: `${xp(a)}%`, width: `${Math.max(0.8, xp(b) - xp(a))}%` }} />
            </span>
            <span className={`wf-val ${cls}`}>{r.total ? '' : r.delta >= 0 ? '+' : '−'}{fmt(Math.abs(r.delta))}</span>
          </div>
        )
      })}
    </div>
  )
}

/** Cost ↔ Value comparison with the net gap called out (t10). */
export function CostValueBars({ cost, value, fmt = (n) => n.toLocaleString() }: {
  cost: number; value: number; fmt?: (n: number) => string
}) {
  const max = Math.max(cost, value, 1)
  const net = value - cost
  return (
    <div className="cvbars">
      <div className="cv-row"><span className="cv-lbl">Cost</span><span className="cv-track"><span className="cv-bar cost" style={{ width: `${(cost / max) * 100}%` }} /></span><span className="cv-val">{fmt(cost)}</span></div>
      <div className="cv-row"><span className="cv-lbl">Value</span><span className="cv-track"><span className="cv-bar value" style={{ width: `${(value / max) * 100}%` }} /></span><span className="cv-val">{fmt(value)}</span></div>
      <div className={`cv-net ${net >= 0 ? 'pos' : 'neg'}`}>Net {net >= 0 ? '+' : '−'}{fmt(Math.abs(net))} <span className="dim">{net >= 0 ? 'profit' : 'shortfall'} this month</span></div>
    </div>
  )
}

/** Horizontal input-vs-output split bar. */
export function SplitBar({ inTok, outTok }: { inTok: number; outTok: number }) {
  const total = inTok + outTok || 1
  return (
    <div>
      <div className="splitbar">
        <span className="in" style={{ width: `${(inTok / total) * 100}%` }} />
        <span className="out" style={{ width: `${(outTok / total) * 100}%` }} />
      </div>
      <div className="splitbar-legend">
        <span><i className="sw in" /> Input {inTok.toLocaleString()} ({Math.round((inTok / total) * 100)}%)</span>
        <span><i className="sw out" /> Output {outTok.toLocaleString()} ({Math.round((outTok / total) * 100)}%)</span>
      </div>
    </div>
  )
}
