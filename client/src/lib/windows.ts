import type { WinKind } from '../types'

/** Slash command → window to open (pure UI nav, no AI round trip). */
export const SLASH_WINDOW: Record<string, WinKind> = {
  '/today': 'today', '/tasks': 'history', '/history': 'history',
  '/agents': 'agents', '/logs': 'logs', '/settings': 'settings',
  '/files': 'files', '/jfiles': 'files', '/backup': 'backups', '/backups': 'backups', '/plugins': 'plugins',
  '/tokens': 'tokens', '/costs': 'tokens',
  '/revenue': 'revenue', '/roi': 'revenue', '/money': 'revenue',
  '/approvals': 'approvals', '/approve': 'approvals',
  '/connectors': 'connectors', '/integrations': 'connectors',
  '/timeline': 'timeline', '/undo': 'undo', '/vision': 'vision',
}

/** Light NL → window navigation: "open/show/go to the <X> window". UI nav only, not an AI task. */
const WINDOW_ALIASES: { kind: WinKind; re: RegExp }[] = [
  { kind: 'approvals', re: /\bapprovals?\b|\bpermissions?\b|\bpending\b/ },
  { kind: 'connectors', re: /\bconnectors?\b|\bintegrations?\b/ },
  { kind: 'timeline', re: /\btimeline\b|\bepisodic\b/ },
  { kind: 'undo', re: /\bundo\b/ },
  { kind: 'vision', re: /\bvision\b|\bdescribe image\b/ },
  { kind: 'revenue', re: /\brevenue\b|\broi\b|\bmoney\b|\bincome\b|\bearnings?\b/ },
  { kind: 'tokens', re: /\btokens?\b|\btoken usage\b|\bcosts?\b/ },
  { kind: 'files', re: /\bfiles?\b|\bexplorer\b/ },
  { kind: 'backups', re: /\bbackups?\b/ },
  { kind: 'plugins', re: /\bplugins?\b|\bmarketplace\b/ },
  { kind: 'settings', re: /\bsettings\b|\bpreferences\b/ },
  { kind: 'agents', re: /\bagents?\b/ },
  { kind: 'today', re: /\btoday\b|\bdigest\b|\bbriefing\b/ },
  { kind: 'history', re: /\bhistory\b|\btasks?\b/ },
  { kind: 'logs', re: /\blogs?\b|\bactivity\b/ },
  { kind: 'conversation', re: /\bchat\b|\bconversation\b/ },
]

export function matchWindowOpen(text: string): WinKind | null {
  const t = text.toLowerCase()
  if (!/^(open|show|go to|launch|bring up|display)\b/.test(t)) return null
  return WINDOW_ALIASES.find((a) => a.re.test(t))?.kind ?? null
}

export const WIN_META: Record<WinKind, { title: string; subtitle: string; dim: string }> = {
  conversation: { title: 'Conversation', subtitle: 'Your chat with Jarvis', dim: '720×620' },
  today: { title: 'Jarvis Today', subtitle: 'Daily digest — counts, highlights', dim: '720×600' },
  history: { title: 'Multi-step history', subtitle: 'Prompts and their sub-step trees', dim: '900×620' },
  settings: { title: 'Settings', subtitle: 'Voice · models · privacy', dim: '880×640' },
  agents: { title: 'Agents', subtitle: 'The roster', dim: '760×560' },
  logs: { title: 'Activity log', subtitle: 'Recent audited actions', dim: '760×560' },
  files: { title: 'Explorer', subtitle: 'Browse · view · edit files', dim: '880×620' },
  backups: { title: 'Backups', subtitle: 'Snapshot · restore the Explorer', dim: '720×560' },
  plugins: { title: 'Plugins', subtitle: 'Installed · marketplace', dim: '820×620' },
  tokens: { title: 'Token usage', subtitle: 'Spend & tokens per model', dim: '860×640' },
  revenue: { title: 'RevenueOS', subtitle: 'ROI — does Jarvis out-earn its cost?', dim: '820×640' },
  approvals: { title: 'Approval Center', subtitle: 'Actions waiting on your permission', dim: '720×600' },
  connectors: { title: 'Connectors', subtitle: 'Integrations · credentials · status', dim: '760×620' },
  timeline: { title: 'Timeline', subtitle: 'What Jarvis did, day by day', dim: '720×600' },
  undo: { title: 'Undo', subtitle: 'Reverse a recent action', dim: '620×520' },
  vision: { title: 'Vision', subtitle: 'Describe / read an image', dim: '680×560' },
  notifications: { title: 'Notifications', subtitle: 'Recent alerts', dim: '520×520' },
  result: { title: 'Result', subtitle: '', dim: '720×520' },
  response: { title: 'Jarvis', subtitle: 'Response', dim: '660×440' },
}
