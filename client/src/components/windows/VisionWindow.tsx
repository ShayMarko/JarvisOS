import { useState } from 'react'
import { describeImage } from '../../api'

export function VisionWindow() {
  const [path, setPath] = useState('')
  const [question, setQuestion] = useState('')
  const [result, setResult] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const run = () => {
    if (!path.trim()) return
    setBusy(true); setResult(null)
    describeImage(path.trim(), question.trim() || undefined)
      .then((r) => setResult(r.result))
      .catch((e) => setResult((e as Error).message))
      .finally(() => setBusy(false))
  }

  return (
    <>
      <div className="vision-form">
        <label className="vision-lbl">Image path <span className="dim">(under the Explorer)</span></label>
        <input className="rev-in" type="text" placeholder="e.g. Screenshots/error.png"
          value={path} onChange={(e) => setPath(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') run() }} />
        <label className="vision-lbl">Question <span className="dim">(optional)</span></label>
        <input className="rev-in" type="text" placeholder="e.g. what error is shown?"
          value={question} onChange={(e) => setQuestion(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') run() }} />
        <button className="hint" disabled={busy || !path.trim()} onClick={run}>{busy ? 'Looking…' : '👁 Describe image'}</button>
      </div>
      {result && <div className="vision-result">{result}</div>}
    </>
  )
}
