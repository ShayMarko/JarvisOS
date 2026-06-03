import { useEffect, useState } from 'react'

/**
 * Live "still working" indicator for an in-progress turn: ticks every second on its OWN interval
 * (so only this small node re-renders, not the whole app) and shows elapsed mm:ss + step count.
 * This is the reassurance that a long multi-step task is genuinely progressing, not stalled.
 */
export function LiveProgress({ startedAt, steps }: { startedAt?: number; steps: number }) {
  const [, setTick] = useState(0)
  useEffect(() => { const h = setInterval(() => setTick((n) => n + 1), 1000); return () => clearInterval(h) }, [])
  const secs = startedAt ? Math.max(0, Math.floor((Date.now() - startedAt) / 1000)) : 0
  const mmss = `${Math.floor(secs / 60)}:${String(secs % 60).padStart(2, '0')}`
  return (
    <div className="substep live">
      <span className="kind"><span className="spin-fast">◠</span></span>
      <span className="det">working… · {steps} step{steps === 1 ? '' : 's'} · {mmss}</span>
    </div>
  )
}
