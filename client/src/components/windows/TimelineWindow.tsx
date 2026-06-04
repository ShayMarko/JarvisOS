import { useCallback, useEffect, useState } from 'react'
import { getTimeline } from '../../api'
import type { TimelineDay } from '../../api'

export function TimelineWindow() {
  const [days, setDays] = useState<TimelineDay[] | null>(null)
  const [n, setN] = useState(7)
  const refresh = useCallback((count: number) => { getTimeline(count).then(setDays).catch(() => setDays([])) }, [])
  useEffect(() => { refresh(n) }, [refresh, n])

  if (!days) return <div className="w-empty"><span className="spin-fast">◠</span></div>

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={() => refresh(n)}>⟳ Refresh</button>
        <span className="grow" />
        {[7, 14, 30].map((d) => (
          <button key={d} className={`tok-tab${n === d ? ' on' : ''}`} onClick={() => setN(d)}>{d}d</button>
        ))}
      </div>
      <div className="tl-list">
        {days.map((d) => (
          <div className="tl-day" key={d.id || d.day}>
            <div className="tl-date">{d.day}</div>
            <div className="tl-summary">{d.summary}</div>
          </div>
        ))}
      </div>
    </>
  )
}
