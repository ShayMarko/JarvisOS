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
