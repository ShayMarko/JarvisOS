import { useState } from 'react'
import { getTimeline } from '../../api'
import { useFetch } from '../../lib/useFetch'

export function TimelineWindow() {
  const [n, setN] = useState(7)
  const { data: days, refresh } = useFetch(() => getTimeline(n), [], [n])

  if (!days) return <div className="w-empty"><span className="spin-fast">◠</span></div>

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
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
