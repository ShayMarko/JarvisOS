import type { Win } from '../types'
import { AgentsWindow } from './windows/AgentsWindow'
import { BackupsWindow } from './windows/BackupsWindow'
import { FilesWindow } from './windows/FilesWindow'
import { HistoryWindow } from './windows/HistoryWindow'
import { LogsWindow } from './windows/LogsWindow'
import { NotificationsWindow } from './windows/NotificationsWindow'
import { PluginsWindow } from './windows/PluginsWindow'
import { ResponseWindow } from './windows/ResponseWindow'
import { RevenueWindow } from './windows/RevenueWindow'
import { ResultWindow } from './windows/ResultWindow'
import { SettingsWindow } from './windows/SettingsWindow'
import { TodayWindow } from './windows/TodayWindow'
import { TokensWindow } from './windows/TokensWindow'

/** Maps a window kind to its body. The 'conversation' window is rendered by App (it needs live turns). */
export function WindowBody({ win }: { win: Win }) {
  switch (win.kind) {
    case 'today': return <TodayWindow />
    case 'history': return <HistoryWindow />
    case 'agents': return <AgentsWindow />
    case 'logs': return <LogsWindow />
    case 'files': return <FilesWindow />
    case 'backups': return <BackupsWindow />
    case 'plugins': return <PluginsWindow />
    case 'tokens': return <TokensWindow />
    case 'revenue': return <RevenueWindow />
    case 'settings': return <SettingsWindow />
    case 'notifications': return <NotificationsWindow />
    case 'result': return <ResultWindow payload={win.payload} />
    case 'response': return <ResponseWindow payload={win.payload} />
    default: return null
  }
}
