import type { SphereKind } from '../types'

const CORE_GLOW = { filter: 'drop-shadow(0 0 6px var(--accent))' }

/** The HUD centerpiece — picks one of three reticle styles. */
export function Sphere({ kind, busy, caption }: { kind: SphereKind; busy: boolean; caption: string }) {
  return (
    <div className={`orb${busy ? ' busy' : ''}`}>
      <svg viewBox="0 0 200 200" aria-hidden>
        {kind === 'gyro' && <Gyro />}
        {kind === 'orbital' && <Orbital />}
        {kind === 'halo' && <Halo />}
      </svg>
      <div className="caption">{caption}</div>
    </div>
  )
}

/* V1 — gyroscopic reticle: broken arcs at tilts + faint wire-globe + calm core */
function Gyro() {
  const merid = [
    { rx: 56, ry: 56 }, { rx: 44, ry: 56 }, { rx: 28, ry: 56 }, { rx: 12, ry: 56 },
    { rx: 56, ry: 44 }, { rx: 56, ry: 28 }, { rx: 56, ry: 12 },
  ]
  return (
    <g stroke="currentColor">
      <g className="spin-cw">
        <circle className="stroke" cx="100" cy="100" r="94" strokeWidth="1" strokeDasharray="200 40 70 30" opacity="0.7" />
      </g>
      <g className="spin-ccw">
        <ellipse className="stroke" cx="100" cy="100" rx="82" ry="34" strokeWidth="1" strokeDasharray="120 30" opacity="0.55" transform="rotate(32 100 100)" />
      </g>
      <g className="spin-mid">
        <ellipse className="stroke" cx="100" cy="100" rx="72" ry="26" strokeWidth="1" opacity="0.4" transform="rotate(-42 100 100)" />
      </g>
      <g className="spin-cw" opacity="0.22">
        {merid.map((m, i) => <ellipse key={i} className="stroke" cx="100" cy="100" rx={m.rx} ry={m.ry} strokeWidth="0.8" />)}
        <circle className="stroke" cx="100" cy="100" r="56" strokeWidth="0.8" />
      </g>
      <circle className="core" cx="100" cy="100" r="6.5" fill="currentColor" style={CORE_GLOW} />
      <circle className="core" cx="100" cy="100" r="15" fill="none" stroke="currentColor" strokeWidth="1" opacity="0.5" />
    </g>
  )
}

/* V2 — orrery: thin globe + 3 tilted rings with nodes, slow precession */
function Orbital() {
  const rings = [0, 60, 120]
  return (
    <g stroke="currentColor">
      <circle className="stroke" cx="100" cy="100" r="60" strokeWidth="0.8" opacity="0.25" />
      <g className="spin-cw">
        {rings.map((deg, i) => (
          <g key={i} transform={`rotate(${deg} 100 100)`} opacity="0.6">
            <ellipse className="stroke" cx="100" cy="100" rx="86" ry="30" strokeWidth="1" />
            <circle cx="186" cy="100" r="3.2" fill="currentColor" style={CORE_GLOW} transform={`rotate(${deg} 100 100)`} />
          </g>
        ))}
      </g>
      <circle className="core" cx="100" cy="100" r="40" fill="currentColor" opacity="0.06" />
      <circle className="core" cx="100" cy="100" r="7" fill="currentColor" style={CORE_GLOW} />
    </g>
  )
}

/* V3 — halo: single luminous ring + traveling arc + soft core (most minimal) */
function Halo() {
  return (
    <g stroke="currentColor">
      <circle className="stroke" cx="100" cy="100" r="80" strokeWidth="1.2" opacity="0.32" />
      <g className="spin-cw">
        <circle className="stroke" cx="100" cy="100" r="80" strokeWidth="2" strokeLinecap="round" strokeDasharray="70 432" opacity="0.95" />
      </g>
      <g className="spin-ccw">
        <circle className="stroke" cx="100" cy="100" r="64" strokeWidth="1" strokeDasharray="24 400" opacity="0.6" />
      </g>
      <circle className="core" cx="100" cy="100" r="44" fill="currentColor" opacity="0.05" />
      <circle className="core" cx="100" cy="100" r="6" fill="currentColor" style={CORE_GLOW} />
    </g>
  )
}
