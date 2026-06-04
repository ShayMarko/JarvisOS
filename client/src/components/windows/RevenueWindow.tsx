import { useCallback, useEffect, useState } from 'react'
import { getRoi, logRevenue } from '../../api'
import type { RoiSnapshot } from '../../api'

const KINDS = [
  { v: 'REVENUE', label: 'Revenue', hint: 'Money earned ($)', unit: '$' },
  { v: 'SAVED', label: 'Saved', hint: 'Money saved ($)', unit: '$' },
  { v: 'HOURS', label: 'Hours', hint: 'Hours of work saved', unit: 'h' },
  { v: 'ASSET', label: 'Asset', hint: 'A sellable asset created', unit: '×' },
  { v: 'EXPERIMENT', label: 'Experiment', hint: 'An income experiment started', unit: '×' },
] as const

const usd = (n: number) => '$' + n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function RevenueWindow() {
  const [data, setData] = useState<RoiSnapshot | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [kind, setKind] = useState<string>('REVENUE')
  const [amount, setAmount] = useState<string>('')
  const [note, setNote] = useState<string>('')
  const [saving, setSaving] = useState(false)

  const refresh = useCallback(() => {
    getRoi().then((d) => { setData(d); setErr(null) }).catch((e) => setErr((e as Error).message))
  }, [])
  useEffect(() => { refresh() }, [refresh])

  const submit = useCallback(() => {
    const amt = parseFloat(amount)
    setSaving(true)
    logRevenue(kind, Number.isFinite(amt) ? amt : 1, note.trim() || undefined)
      .then((d) => { setData(d); setErr(null); setAmount(''); setNote('') })
      .catch((e) => setErr((e as Error).message))
      .finally(() => setSaving(false))
  }, [kind, amount, note])

  if (!data && !err) return <div className="w-empty"><span className="spin-fast">◠</span></div>

  const covers = data?.coversCost
  const roiPct = data && data.monthlyCost > 0 ? Math.round((data.valueGenerated / data.monthlyCost) * 100) : null
  const barValue = data ? Math.min(100, data.monthlyCost > 0 ? (data.valueGenerated / data.monthlyCost) * 100 : (data.valueGenerated > 0 ? 100 : 0)) : 0

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        <span className="note">{data?.period ?? 'month-to-date'}</span>
      </div>

      {err && <div className="w-empty" style={{ color: 'var(--bad, #f88)' }}>{err}</div>}

      {data && (
        <>
          {/* ---- the headline verdict ---- */}
          <div className={`roi-verdict ${covers ? 'good' : 'bad'}`}>
            <div className="roi-icon">{covers ? '✓' : '…'}</div>
            <div className="roi-text">
              <div className="roi-head">
                {covers ? 'Jarvis is paying for itself' : 'Not yet covering its cost'}
              </div>
              <div className="roi-sub">
                {usd(data.valueGenerated)} value vs {usd(data.monthlyCost)} cost
                {roiPct !== null && <> · <b>{roiPct}%</b> of cost recovered</>}
              </div>
            </div>
            <div className="roi-mult">{data.roi.toLocaleString()}×</div>
          </div>

          <div className="budget-meter" style={{ marginTop: 10 }}>
            <div className="fill" style={{ width: `${barValue}%`, background: covers ? 'var(--ok, #4ad295)' : undefined }} />
          </div>

          {/* ---- top cards ---- */}
          <div className="cards3" style={{ marginTop: 14 }}>
            <div className="scard"><div className="lbl">VALUE GENERATED</div><div className="big">{usd(data.valueGenerated)}</div><div className="sub">revenue + saved + time</div></div>
            <div className="scard"><div className="lbl">MONTHLY COST</div><div className="big">{usd(data.monthlyCost)}</div><div className="sub">{usd(data.monthlyAiCost)} AI · {usd(data.monthlyBaseCost)} base</div></div>
            <div className="scard"><div className="lbl">ROI</div><div className="big">{data.roi.toLocaleString()}×</div><div className="sub">{covers ? 'covers its cost' : 'below break-even'}</div></div>
          </div>

          {/* ---- breakdown ---- */}
          <div className="dv-sec-title" style={{ marginTop: 16 }}>This month's breakdown</div>
          <table className="tok-table">
            <tbody>
              <tr><td>💵 Revenue earned</td><td className="num">{usd(data.revenue)}</td></tr>
              <tr><td>🏦 Money saved</td><td className="num">{usd(data.moneySaved)}</td></tr>
              <tr><td>⏱️ Hours saved</td><td className="num">{data.hoursSaved.toLocaleString()} h <span className="dim">→ {usd(data.hoursValue)}</span></td></tr>
              <tr><td>📦 Sellable assets created</td><td className="num">{data.assetsCreated.toLocaleString()}</td></tr>
              <tr><td>🧪 Active experiments</td><td className="num">{data.activeExperiments.toLocaleString()}</td></tr>
            </tbody>
          </table>

          {/* ---- log a new entry ---- */}
          <div className="dv-sec-title" style={{ marginTop: 16 }}>Log an entry</div>
          <div className="tok-tabs">
            {KINDS.map((k) => (
              <button key={k.v} className={`tok-tab${kind === k.v ? ' on' : ''}`} onClick={() => setKind(k.v)} title={k.hint}>{k.label}</button>
            ))}
          </div>
          <div className="rev-form">
            <input className="rev-in" type="number" inputMode="decimal" placeholder={KINDS.find((k) => k.v === kind)?.hint ?? 'Amount'}
              value={amount} onChange={(e) => setAmount(e.target.value)} />
            <input className="rev-in grow" type="text" placeholder="Note (optional)"
              value={note} onChange={(e) => setNote(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') submit() }} />
            <button className="hint" onClick={submit} disabled={saving}>{saving ? '…' : 'Log'}</button>
          </div>
        </>
      )}
    </>
  )
}
