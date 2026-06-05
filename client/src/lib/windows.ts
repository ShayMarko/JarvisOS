import type { WinKind } from '../types'

/** Slash command → window to open (pure UI nav, no AI round trip). */
export const SLASH_WINDOW: Record<string, WinKind> = {
  '/activity': 'activity', '/today': 'activity', '/tasks': 'activity', '/history': 'activity',
  '/timeline': 'activity', '/runs': 'activity',
  '/capabilities': 'capabilities', '/agents': 'capabilities',
  '/connectors': 'capabilities', '/integrations': 'capabilities', '/plugins': 'capabilities', '/marketplace': 'capabilities',
  '/logs': 'logs', '/settings': 'settings',
  '/files': 'files', '/jfiles': 'files', '/backup': 'backups', '/backups': 'backups',
  '/usage': 'usage', '/tokens': 'usage', '/costs': 'usage',
  '/revenue': 'usage', '/roi': 'usage', '/money': 'usage',
  '/approvals': 'approvals', '/approve': 'approvals',
  '/undo': 'undo', '/vision': 'vision', '/discord': 'discord',
}

/** For the merged tabbed windows (Activity / Capabilities): which tab a slash opens on. */
export const SLASH_TAB: Record<string, string> = {
  '/timeline': 'timeline', '/history': 'runs', '/tasks': 'runs', '/runs': 'runs',
  '/connectors': 'connectors', '/integrations': 'connectors', '/plugins': 'plugins', '/marketplace': 'plugins',
  '/revenue': 'revenue', '/roi': 'revenue', '/money': 'revenue',
}

/** Light NL → window navigation: "open/show/go to the <X> window". UI nav only, not an AI task. */
const WINDOW_ALIASES: { kind: WinKind; re: RegExp }[] = [
  { kind: 'approvals', re: /\bapprovals?\b|\bpermissions?\b|\bpending\b/ },
  { kind: 'capabilities', re: /\bconnectors?\b|\bintegrations?\b|\bplugins?\b|\bmarketplace\b|\bagents?\b|\bcapabilities\b/ },
  { kind: 'activity', re: /\btimeline\b|\bepisodic\b|\bactivity\b|\bruns?\b/ },
  { kind: 'undo', re: /\bundo\b/ },
  { kind: 'vision', re: /\bvision\b|\bdescribe image\b/ },
  { kind: 'discord', re: /\bdiscord\b/ },
  { kind: 'usage', re: /\brevenue\b|\broi\b|\bmoney\b|\bincome\b|\bearnings?\b|\btokens?\b|\btoken usage\b|\bcosts?\b|\busage\b/ },
  { kind: 'files', re: /\bfiles?\b|\bexplorer\b/ },
  { kind: 'backups', re: /\bbackups?\b/ },
  { kind: 'settings', re: /\bsettings\b|\bpreferences\b/ },
  { kind: 'activity', re: /\btoday\b|\bdigest\b|\bbriefing\b|\bhistory\b|\btasks?\b/ },
  { kind: 'logs', re: /\blogs?\b/ },
  { kind: 'conversation', re: /\bchat\b|\bconversation\b/ },
]

export function matchWindowOpen(text: string): WinKind | null {
  const t = text.toLowerCase()
  if (!/^(open|show|go to|launch|bring up|display)\b/.test(t)) return null
  return WINDOW_ALIASES.find((a) => a.re.test(t))?.kind ?? null
}

export const WIN_META: Record<WinKind, { title: string; subtitle: string; dim: string }> = {
  conversation: { title: 'Conversation', subtitle: 'Your chat with Jarvis', dim: '720×620' },
  activity: { title: 'Activity', subtitle: 'Today · Timeline · Runs', dim: '900×620' },
  settings: { title: 'Settings', subtitle: 'Voice · models · privacy', dim: '880×640' },
  capabilities: { title: 'Capabilities', subtitle: 'Agents · Connectors · Plugins', dim: '860×640' },
  logs: { title: 'Activity log', subtitle: 'Recent audited actions', dim: '760×560' },
  files: { title: 'Explorer', subtitle: 'Browse · view · edit files', dim: '880×620' },
  backups: { title: 'Backups', subtitle: 'Snapshot · restore the Explorer', dim: '720×560' },
  usage: { title: 'Usage', subtitle: 'Tokens · Revenue (ROI)', dim: '880×660' },
  approvals: { title: 'Approval Center', subtitle: 'Actions waiting on your permission', dim: '720×600' },
  undo: { title: 'Undo', subtitle: 'Reverse a recent action', dim: '620×520' },
  vision: { title: 'Vision', subtitle: 'Describe / read an image', dim: '680×560' },
  discord: { title: 'Discord', subtitle: 'Control-channel status · test', dim: '680×600' },
  notifications: { title: 'Notifications', subtitle: 'Recent alerts', dim: '520×520' },
  result: { title: 'Result', subtitle: '', dim: '720×520' },
  response: { title: 'Jarvis', subtitle: 'Response', dim: '660×440' },
}
