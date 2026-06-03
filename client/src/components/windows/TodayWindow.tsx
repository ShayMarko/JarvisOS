import { useEffect, useState } from 'react'
import { runCommand } from '../../api'

export function TodayWindow() {
  const [text, setText] = useState<string | null>(null)
  useEffect(() => { runCommand('/today').then((r) => setText(r.message)).catch((e) => setText(String(e))) }, [])
  return (
    <div className="digest-card">
      <div className="digest-head">
        <div><div className="t">✦ JARVIS TODAY</div><div className="s">What Jarvis has done recently</div></div>
        <div className="w-tabs"><span className="w-tab on">24h</span><span className="w-tab">7d</span></div>
      </div>
      <div className="digest">{text === null ? <div className="w-empty"><span className="spin-fast">◠</span></div> : <pre>{text}</pre>}</div>
    </div>
  )
}
