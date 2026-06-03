import { useEffect, useState } from 'react'

/** Self-contained clock — owns its own 1s tick so the second-hand doesn't re-render the whole HUD. */
export function Clock() {
  const [now, setNow] = useState(new Date())
  useEffect(() => { const t = setInterval(() => setNow(new Date()), 1000); return () => clearInterval(t) }, [])
  return (
    <>
      <div className="clock">{now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</div>
      <div className="datestr">{now.toLocaleDateString([], { weekday: 'short', month: 'short', day: '2-digit', year: 'numeric' }).toUpperCase()}</div>
    </>
  )
}
