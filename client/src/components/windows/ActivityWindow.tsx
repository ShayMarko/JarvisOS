import { useState } from 'react'
import { HistoryWindow } from './HistoryWindow'
import { TimelineWindow } from './TimelineWindow'
import { TodayWindow } from './TodayWindow'

const TABS = [
  { id: 'today', label: 'Today' },
  { id: 'timeline', label: 'Timeline' },
  { id: 'runs', label: 'Runs' },
] as const
type TabId = (typeof TABS)[number]['id']

/** Unified Activity window — three views of "what happened over time": Today (now), Timeline (days),
 * Runs (prompt → sub-step history). Each tab renders the existing standalone window body. */
export function ActivityWindow({ initialTab }: { initialTab?: string }) {
  const start = TABS.some((t) => t.id === initialTab) ? (initialTab as TabId) : 'today'
  const [tab, setTab] = useState<TabId>(start)
  return (
    <>
      <div className="tok-tabs">
        {TABS.map((t) => (
          <button key={t.id} className={`tok-tab${tab === t.id ? ' on' : ''}`} onClick={() => setTab(t.id)}>{t.label}</button>
        ))}
      </div>
      {tab === 'today' && <TodayWindow />}
      {tab === 'timeline' && <TimelineWindow />}
      {tab === 'runs' && <HistoryWindow />}
    </>
  )
}
