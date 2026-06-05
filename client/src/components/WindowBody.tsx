import type { Win } from '../types'
import { ActivityWindow } from './windows/ActivityWindow'
import { BackupsWindow } from './windows/BackupsWindow'
import { CapabilitiesWindow } from './windows/CapabilitiesWindow'
import { DiscordWindow } from './windows/DiscordWindow'
import { FilesWindow } from './windows/FilesWindow'
import { LogsWindow } from './windows/LogsWindow'
import { NotificationsWindow } from './windows/NotificationsWindow'
import { ResponseWindow } from './windows/ResponseWindow'
import { ApprovalsWindow } from './windows/ApprovalsWindow'
import { UndoWindow } from './windows/UndoWindow'
import { UsageWindow } from './windows/UsageWindow'
import { VisionWindow } from './windows/VisionWindow'
import { ResultWindow } from './windows/ResultWindow'
import { SettingsWindow } from './windows/SettingsWindow'

/** Maps a window kind to its body. The 'conversation' window is rendered by App (it needs live turns). */
export function WindowBody({ win }: { win: Win }) {
  switch (win.kind) {
    case 'activity': return <ActivityWindow initialTab={win.payload as string | undefined} />
    case 'capabilities': return <CapabilitiesWindow initialTab={win.payload as string | undefined} />
    case 'logs': return <LogsWindow />
    case 'files': return <FilesWindow />
    case 'backups': return <BackupsWindow />
    case 'usage': return <UsageWindow initialTab={win.payload as string | undefined} />
    case 'approvals': return <ApprovalsWindow />
    case 'undo': return <UndoWindow />
    case 'vision': return <VisionWindow />
    case 'discord': return <DiscordWindow />
    case 'settings': return <SettingsWindow />
    case 'notifications': return <NotificationsWindow />
    case 'result': return <ResultWindow payload={win.payload} />
    case 'response': return <ResponseWindow payload={win.payload} />
    default: return null
  }
}
