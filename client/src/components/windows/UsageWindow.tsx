import { useState } from 'react'
import { RevenueWindow } from './RevenueWindow'
import { TokensWindow } from './TokensWindow'

const TABS = [
  { id: 'tokens', label: 'Tokens' },
  { id: 'revenue', label: 'Revenue' },
] as const
type TabId = (typeof TABS)[number]['id']

/** Unified Usage window — the money picture: Tokens (AI spend/usage) and Revenue (RevenueOS ROI). */
export function UsageWindow({ initialTab }: { initialTab?: string }) {
  const start = TABS.some((t) => t.id === initialTab) ? (initialTab as TabId) : 'tokens'
  const [tab, setTab] = useState<TabId>(start)
  return (
    <>
      <div className="tok-tabs">
        {TABS.map((t) => (
          <button key={t.id} className={`tok-tab${tab === t.id ? ' on' : ''}`} onClick={() => setTab(t.id)}>{t.label}</button>
        ))}
      </div>
      {tab === 'tokens' && <TokensWindow />}
      {tab === 'revenue' && <RevenueWindow />}
    </>
  )
}
