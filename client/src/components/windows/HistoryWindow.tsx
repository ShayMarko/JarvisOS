import { useEffect, useMemo, useState } from 'react'
import { getRuns } from '../../api'
import type { RunRecord } from '../../api'
import { ago, parseSteps, runState } from '../../lib/format'

function RunRow({ run }: { run: RunRecord }) {
  const [open, setOpen] = useState(false)
  const steps = useMemo(() => parseSteps(run.stepsJson), [run.stepsJson])
  const st = runState(run.status)
  return (
    <div className={`run-row${open ? ' open' : ''}`}>
      <div className="head" onClick={() => setOpen((o) => !o)}>
        <span className="chev">▶</span>
        <span className="grow" style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{run.request}</span>
        <span className="mono" style={{ color: 'var(--muted)', fontSize: 10 }}>{run.agent}</span>
        <span className={`pill ${st === 'success' ? 'done' : st === 'failed' ? 'failed' : 'running'}`}>{run.status}</span>
        <span className="when">{ago(run.createdAt)}</span>
      </div>
      {open && (
        <div className="substeps">
          {steps.length === 0 && <div className="substep"><span className="det">No recorded sub-steps.</span></div>}
          {steps.map((s, i) => (
            <div className="substep" key={i}><span className="kind">{s.kind}</span><span className="lbl">{s.label}{s.detail ? <span className="det"> — {s.detail}</span> : null}</span></div>
          ))}
          {run.answer && <div className="substep"><span className="kind">answer</span><span className="lbl" style={{ whiteSpace: 'pre-wrap' }}>{run.answer.slice(0, 400)}</span></div>}
        </div>
      )}
    </div>
  )
}

export function HistoryWindow() {
  const [runs, setRuns] = useState<RunRecord[] | null>(null)
  const [tab, setTab] = useState<'all' | 'running' | 'success' | 'failed'>('all')
  const [q, setQ] = useState('')
  useEffect(() => { getRuns(60).then(setRuns).catch(() => setRuns([])) }, [])
  const filtered = (runs ?? []).filter((r) => (tab === 'all' || runState(r.status) === tab) && r.request.toLowerCase().includes(q.toLowerCase()))
  return (
    <>
      <div className="w-search">🔍 <input placeholder="Filter by prompt text…" value={q} onChange={(e) => setQ(e.target.value)} />
        <div className="w-tabs">{(['all', 'running', 'success', 'failed'] as const).map((t) => <button key={t} className={`w-tab${tab === t ? ' on' : ''}`} onClick={() => setTab(t)}>{t.toUpperCase()}</button>)}</div>
        <span className="mono" style={{ color: 'var(--muted)', fontSize: 11 }}>{filtered.length}/{runs?.length ?? 0}</span>
      </div>
      {!runs ? <div className="w-empty"><span className="spin-fast">◠</span></div>
        : filtered.length === 0 ? <div className="w-empty"><div className="t">No prompts yet</div><div className="s">Ask Jarvis something — like “take a screenshot and turn it into a PDF” — and the sub-step tree shows up here.</div></div>
        : <div className="rows">{filtered.map((r) => <RunRow key={r.id} run={r} />)}</div>}
    </>
  )
}
