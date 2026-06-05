import { useState } from 'react'
import { AgentsWindow } from './AgentsWindow'
import { ConnectorsWindow } from './ConnectorsWindow'
import { PluginsWindow } from './PluginsWindow'

const TABS = [
  { id: 'agents', label: 'Agents' },
  { id: 'connectors', label: 'Connectors' },
  { id: 'plugins', label: 'Plugins' },
] as const
type TabId = (typeof TABS)[number]['id']

/** Unified Capabilities window — what Jarvis is made of and can use: its Agents (roster),
 * Connectors (external integrations + credentials), and Plugins (installed add-ons + marketplace). */
export function CapabilitiesWindow({ initialTab }: { initialTab?: string }) {
  const start = TABS.some((t) => t.id === initialTab) ? (initialTab as TabId) : 'agents'
  const [tab, setTab] = useState<TabId>(start)
  return (
    <>
      <div className="tok-tabs">
        {TABS.map((t) => (
          <button key={t.id} className={`tok-tab${tab === t.id ? ' on' : ''}`} onClick={() => setTab(t.id)}>{t.label}</button>
        ))}
      </div>
      {tab === 'agents' && <AgentsWindow />}
      {tab === 'connectors' && <ConnectorsWindow />}
      {tab === 'plugins' && <PluginsWindow />}
    </>
  )
}
