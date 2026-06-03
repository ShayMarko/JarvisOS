import { useCallback, useEffect, useState } from 'react'
import { getSettings, getTokenDashboard } from '../../api'
import type { SettingsView, TokenDashboard } from '../../api'
import { CHART_COLORS, Donut, Spark, SplitBar } from '../charts'

const PROVIDER_LABEL: Record<string, string> = { ollama: 'Ollama', openai: 'OpenAI', anthropic: 'Anthropic', mock: 'Mock' }
const provLabel = (p: string | null) => (p ? PROVIDER_LABEL[p] ?? p : '—')

function shortTime(at: string | null): string {
  if (!at) return '—'
  try { return new Date(at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) } catch { return '—' }
}

export function TokensWindow() {
  const [data, setData] = useState<TokenDashboard | null>(null)
  const [budget, setBudgetState] = useState<SettingsView['budget'] | null>(null)
  const [sel, setSel] = useState<string | null>(null)   // selected provider, or null = All
  const refresh = useCallback(() => {
    getTokenDashboard(300).then(setData).catch(() => setData(null))
    getSettings().then((s) => setBudgetState(s.budget ?? null)).catch(() => {})
  }, [])
  useEffect(() => { refresh() }, [refresh])

  if (!data) return <div className="w-empty"><span className="spin-fast">◠</span></div>
  const providers = data.providers
  const pie = providers.filter((m) => m.totalTokens > 0).map((m) => ({ label: m.provider, value: m.totalTokens }))
  const maxTokens = Math.max(1, ...providers.map((m) => m.totalTokens))
  const maxCost = Math.max(0.0000001, ...providers.map((m) => m.cost))
  const colorOf = (provider: string) => CHART_COLORS[Math.max(0, providers.findIndex((m) => m.provider === provider)) % CHART_COLORS.length]
  const detail = sel ? providers.find((m) => m.provider === sel) : null
  const recent = [...data.timeline].reverse().filter((t) => !detail || t.provider === detail.provider).slice(0, 14)

  const runsTable = (
    <table className="tok-table">
      <thead><tr><th>Time</th><th>Provider</th><th>Model</th><th className="num">Tokens</th><th className="num">Cost</th></tr></thead>
      <tbody>
        {recent.length === 0 ? <tr><td colSpan={5} className="empty">No runs yet on a real AI provider.</td></tr>
          : recent.map((t, i) => (
            <tr key={i}>
              <td className="mono">{shortTime(t.at)}</td>
              <td><span className="dot" style={{ background: colorOf(t.provider ?? '') }} /> {provLabel(t.provider)}</td>
              <td className="mono dim">{t.model ?? '—'}</td>
              <td className="num">{t.tokens.toLocaleString()}</td>
              <td className="num dim">${t.cost.toFixed(4)}</td>
            </tr>))}
      </tbody>
    </table>
  )

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        <span className="note">{data.runs} AI runs analysed</span>
      </div>

      <div className="tok-tabs">
        <button className={`tok-tab${sel === null ? ' on' : ''}`} onClick={() => setSel(null)}>All providers</button>
        {providers.map((m) => (
          <button key={m.provider} className={`tok-tab${sel === m.provider ? ' on' : ''}`} onClick={() => setSel(m.provider)}>
            <span className="dot" style={{ background: colorOf(m.provider) }} />{provLabel(m.provider)}
          </button>
        ))}
      </div>

      {detail ? (
        /* ----- per-provider detail ----- */
        <>
          <div className="cards3">
            <div className="scard"><div className="lbl">TOTAL TOKENS</div><div className="big">{detail.totalTokens.toLocaleString()}</div><div className="sub">{Math.round((detail.totalTokens / Math.max(1, data.totalTokens)) * 100)}% of all usage</div></div>
            <div className="scard"><div className="lbl">EST. COST</div><div className="big">${detail.cost.toFixed(4)}</div><div className="sub">{detail.runs.toLocaleString()} runs</div></div>
            <div className="scard"><div className="lbl">AVG / RUN</div><div className="big">{Math.round(detail.totalTokens / Math.max(1, detail.runs)).toLocaleString()}</div><div className="sub">tokens per run</div></div>
          </div>
          <div className="dv-sec-title" style={{ marginTop: 14 }}>Input vs output · {provLabel(detail.provider)}</div>
          <SplitBar inTok={detail.promptTokens} outTok={detail.completionTokens} />
          <div className="dv-sec-title" style={{ marginTop: 16 }}>Tokens per run</div>
          <Spark points={data.timeline.filter((t) => t.provider === detail.provider).map((t) => t.tokens)} />
          <div className="dv-sec-title" style={{ marginTop: 16 }}>Recent {provLabel(detail.provider)} runs</div>
          {runsTable}
        </>
      ) : (
        /* ----- all-providers overview ----- */
        <>
          <div className="cards3">
            <div className="scard"><div className="lbl">TOTAL TOKENS</div><div className="big">{data.totalTokens.toLocaleString()}</div><div className="sub">{data.promptTokens.toLocaleString()} in · {data.completionTokens.toLocaleString()} out</div></div>
            <div className="scard"><div className="lbl">EST. COST</div><div className="big">${data.totalCost.toFixed(4)}</div><div className="sub">paid providers only</div></div>
            <div className="scard"><div className="lbl">BUDGET TODAY</div><div className="big">{budget && budget.dailyTokenBudget > 0 ? `${Math.round((budget.tokensToday / budget.dailyTokenBudget) * 100)}%` : '∞'}</div><div className="sub">{budget ? (budget.paused ? 'PAUSED' : budget.dailyTokenBudget > 0 ? `${budget.tokensToday.toLocaleString()} / ${budget.dailyTokenBudget.toLocaleString()}` : 'no cap') : '—'}</div></div>
          </div>
          {budget && budget.dailyTokenBudget > 0 && (
            <div className="budget-meter"><div className="fill" style={{ width: `${Math.min(100, (budget.tokensToday / budget.dailyTokenBudget) * 100)}%` }} /></div>
          )}

          <div className="dv-sec-title" style={{ marginTop: 14 }}>Token share by provider <span className="note">· click to drill in</span></div>
          <div className="tok-split">
            <Donut data={pie.length ? pie : [{ label: 'none', value: 1 }]} />
            <div className="tok-legend">
              {providers.map((m) => (
                <button className="tok-row" key={m.provider} onClick={() => setSel(m.provider)}>
                  <span className="dot" style={{ background: colorOf(m.provider) }} />
                  <span className="nm">{provLabel(m.provider)}</span>
                  <span className="bar"><span style={{ width: `${(m.totalTokens / maxTokens) * 100}%`, background: colorOf(m.provider) }} /></span>
                  <span className="val">{m.totalTokens.toLocaleString()}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="dv-sec-title" style={{ marginTop: 16 }}>Input vs output</div>
          <SplitBar inTok={data.promptTokens} outTok={data.completionTokens} />

          <div className="dv-sec-title" style={{ marginTop: 16 }}>Cost by provider</div>
          <div className="tok-legend">
            {providers.map((m) => (
              <div className="tok-row" key={m.provider} style={{ cursor: 'default' }}>
                <span className="dot" style={{ background: colorOf(m.provider) }} />
                <span className="nm">{provLabel(m.provider)}</span>
                <span className="bar"><span style={{ width: `${(m.cost / maxCost) * 100}%`, background: colorOf(m.provider) }} /></span>
                <span className="val">${m.cost.toFixed(4)}</span>
              </div>
            ))}
          </div>

          <div className="dv-sec-title" style={{ marginTop: 16 }}>Provider breakdown</div>
          <table className="tok-table">
            <thead><tr><th>Provider</th><th className="num">Runs</th><th className="num">Input</th><th className="num">Output</th><th className="num">Total</th><th className="num">Cost</th></tr></thead>
            <tbody>
              {providers.map((m) => (
                <tr key={m.provider}>
                  <td><span className="dot" style={{ background: colorOf(m.provider) }} /> {provLabel(m.provider)}</td>
                  <td className="num">{m.runs.toLocaleString()}</td>
                  <td className="num dim">{m.promptTokens.toLocaleString()}</td>
                  <td className="num dim">{m.completionTokens.toLocaleString()}</td>
                  <td className="num">{m.totalTokens.toLocaleString()}</td>
                  <td className="num dim">${m.cost.toFixed(4)}</td>
                </tr>))}
            </tbody>
          </table>

          <div className="dv-sec-title" style={{ marginTop: 16 }}>Tokens per run (recent → now)</div>
          <Spark points={data.timeline.map((t) => t.tokens)} />

          <div className="dv-sec-title" style={{ marginTop: 16 }}>Recent activity</div>
          {runsTable}
        </>
      )}
    </>
  )
}
