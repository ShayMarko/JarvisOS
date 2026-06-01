// Thin client over the Jarvis Core REST API.

export type CommandStatus = 'OK' | 'ERROR' | 'PENDING_APPROVAL'

export interface CommandResult {
  status: CommandStatus
  type: string // "help" | "files" | "status" | "settings" | "logs" | "message" | "error"
  message: string
  data: unknown
}

export interface CommandDefinition {
  name: string
  slash: string
  voiceAliases: string[]
  description: string
  parameters: string[]
  requiredPermissions: string[]
  visibleInHelp: boolean
  category: string
}

export interface FileNode {
  name: string
  path: string
  directory: boolean
  size: number
  modified: string
}

export interface FileContent {
  path: string
  content: string
}

export type TriggerType = 'MANUAL' | 'SCHEDULE' | 'WEBHOOK'
export type WfStepType = 'COMMAND' | 'BRAIN' | 'CONNECTOR' | 'NOTIFY' | 'APPROVAL'
export type WfRunStatus = 'RUNNING' | 'PAUSED' | 'DONE' | 'FAILED'

export interface WfStep {
  id: string
  name: string
  type: WfStepType
  config: Record<string, unknown>
  maxAttempts: number
}

export interface WorkflowView {
  id: string
  name: string
  description: string | null
  triggerType: TriggerType
  cron: string | null
  enabled: boolean
  scheduled: boolean
  steps: WfStep[]
  createdAt: string
}

export interface WfStepResult {
  stepId: string
  name: string
  type: WfStepType
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'AWAITING_APPROVAL'
  output: string | null
  attempts: number
}

export interface RunView {
  id: string
  workflowId: string
  status: WfRunStatus
  currentStep: number
  trigger: string | null
  startedAt: string
  finishedAt: string | null
  steps: WfStepResult[]
}

export interface WorkflowsData {
  workflows: WorkflowView[]
  runs: RunView[]
}

export interface WorkflowDraft {
  name: string
  description?: string
  triggerType: TriggerType
  cron?: string | null
  enabled?: boolean
  steps: WfStep[]
}

export interface Step {
  kind: string // intent | agent | tool | answer
  label: string
  detail: string | null
}

export interface ChatResponse {
  answer: string
  agent: string
  steps: Step[]
  taskId: string
  tokens: number
  model: string
}

export interface AgentDef {
  name: string
  slug: string
  role: string
  systemPrompt: string
  toolNames: string[]
  category: string
}

export interface TaskItem {
  id: string
  request: string
  agent: string | null
  status: 'RUNNING' | 'DONE' | 'FAILED'
  createdAt: string
  finishedAt: string | null
  summary: string | null
}

export type Sensitivity = 'NORMAL' | 'SENSITIVE'
export type Visibility = 'USER_VISIBLE' | 'INTERNAL'

export interface Memory {
  id: string
  category: string
  title: string
  content: string
  source: string | null
  confidence: number
  visibility: Visibility
  sensitivity: Sensitivity
  createdAt: string
  updatedAt: string
  expiresAt: string | null
  enabled: boolean
}

/** Writable fields for create/update. */
export interface MemoryDraft {
  category?: string
  title?: string
  content?: string
  source?: string | null
  confidence?: number
  visibility?: Visibility
  sensitivity?: Sensitivity
  expiresAt?: string | null
  enabled?: boolean
}

export type ConnectorStatus = 'CONNECTED' | 'DISCONNECTED' | 'ERROR'

export interface ConnectorActionDef {
  id: string
  name: string
  description: string
}

export interface ConnectorInfo {
  id: string
  name: string
  category: string
  requiredSecret: string | null
  status: ConnectorStatus
  actions: ConnectorActionDef[]
}

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'DENIED'

export interface ApprovalRequest {
  id: string
  actionType: string
  title: string
  description: string | null
  riskLevel: RiskLevel
  preview: string | null
  status: ApprovalStatus
  createdAt: string
  decidedAt: string | null
  resultSummary: string | null
}

export interface SandboxResult {
  exitCode: number
  output: string
  durationMs: number
  timedOut: boolean
  workdir: string
}

export interface ApprovalDecision {
  request: ApprovalRequest
  result: unknown
}

export interface SecretView {
  id: string
  name: string
  connector: string | null
  scopes: string[]
  masked: string
  createdAt: string
  lastAccessedAt: string | null
}

export interface AuditEntry {
  id: number
  timestamp: string
  inputType: string
  command: string | null
  input: string
  status: string
  detail: string | null
}

export interface KbDocument {
  id: string
  source: string
  title: string | null
  chunkCount: number
  createdAt: string
}

export interface KbHit {
  documentId: string
  title: string
  source: string
  ordinal: number
  content: string
  score: number
}

export interface KbData {
  documents: KbDocument[]
  query: string
  results: KbHit[]
}

export interface ModelDescriptor {
  id: string
  provider: string
  local: boolean
  costInputPer1k: number
  costOutputPer1k: number
  quality: number
  latencyMs: number
  available: boolean
}

export interface ModelsData {
  models: ModelDescriptor[]
  active: string
  preference: string
}

export interface RunRecord {
  id: string
  taskId: string | null
  sessionId: string | null
  agent: string
  model: string
  request: string
  answer: string
  status: string
  promptTokens: number
  completionTokens: number
  cost: number
  durationMs: number
  stepsJson: string | null
  createdAt: string
}

export interface CostsData {
  runs: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  totalCost: number
  costByModel: Record<string, number>
  runsByAgent: Record<string, number>
}

export interface PluginsData {
  commands: { slash: string; description: string; category: string }[]
  tools: { name: string; description: string }[]
  connectors: { id: string; name: string; status: string }[]
  agents: { slug: string; name: string; role: string }[]
  counts: Record<string, number>
}

export interface NotificationItem {
  id: string
  type: 'info' | 'success' | 'warning' | 'error'
  title: string
  body: string | null
  source: string | null
  read: boolean
  createdAt: string
}

/** Mirrors the backend ApiError envelope. */
export class ApiError extends Error {
  code: string
  detail: string[]
  traceId: string
  constructor(code: string, message: string, detail: string[], traceId: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.detail = detail
    this.traceId = traceId
  }
}

async function req<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (res.status === 204) {
    return undefined as T
  }
  const body = await res.json().catch(() => null)
  if (!res.ok) {
    if (body && body.code) {
      throw new ApiError(body.code, body.message, body.detail ?? [], body.traceId ?? '-')
    }
    throw new ApiError('HTTP_' + res.status, `${res.status} ${res.statusText}`, [], '-')
  }
  return body as T
}

const jsonHeaders = { 'Content-Type': 'application/json' }

/** A stable id for this client session, so the Brain keeps conversation continuity. */
export const sessionId: string =
  (globalThis.crypto?.randomUUID?.() ?? 'sess-' + Math.random().toString(36).slice(2))

export function runCommand(input: string): Promise<CommandResult> {
  return req<CommandResult>('/api/command', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ input, sessionId }),
  })
}

export function revealFile(path: string): Promise<{ result: string }> {
  return req<{ result: string }>('/api/files/reveal', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ path }),
  })
}

export function getNotifications(): Promise<NotificationItem[]> {
  return req<NotificationItem[]>('/api/notifications')
}

export function getUnreadCount(): Promise<number> {
  return req<{ count: number }>('/api/notifications/unread-count').then((r) => r.count)
}

export function markNotificationsRead(): Promise<void> {
  return req<void>('/api/notifications/read-all', { method: 'POST', headers: jsonHeaders })
}

export function indexKb(body: { path?: string; title?: string; content?: string }): Promise<unknown> {
  return req<unknown>('/api/kb/index', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(body) })
}

export function deleteKbDocument(id: string): Promise<void> {
  return req<void>(`/api/kb/${id}`, { method: 'DELETE' })
}

export function replayRun(id: string): Promise<unknown> {
  return req<unknown>(`/api/runs/${id}/replay`, { method: 'POST', headers: jsonHeaders })
}

export function fetchCommands(): Promise<CommandDefinition[]> {
  return req<CommandDefinition[]>('/api/commands')
}

export function getFileContent(path: string): Promise<FileContent> {
  return req<FileContent>(`/api/files/content?path=${encodeURIComponent(path)}`)
}

export function writeFile(path: string, content: string): Promise<FileNode> {
  return req<FileNode>('/api/files/content', {
    method: 'PUT',
    headers: jsonHeaders,
    body: JSON.stringify({ path, content }),
  })
}

export function createDir(path: string): Promise<FileNode> {
  return req<FileNode>('/api/files/dir', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ path }),
  })
}

export function deleteFile(path: string, confirm: boolean): Promise<void> {
  return req<void>(`/api/files?path=${encodeURIComponent(path)}&confirm=${confirm}`, {
    method: 'DELETE',
  })
}

export function createMemory(draft: MemoryDraft): Promise<Memory> {
  return req<Memory>('/api/memory', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(draft),
  })
}

export function updateMemory(id: string, draft: MemoryDraft): Promise<Memory> {
  return req<Memory>(`/api/memory/${id}`, {
    method: 'PUT',
    headers: jsonHeaders,
    body: JSON.stringify(draft),
  })
}

export function deleteMemory(id: string): Promise<void> {
  return req<void>(`/api/memory/${id}`, { method: 'DELETE' })
}

export function exportMemory(): Promise<Memory[]> {
  return req<Memory[]>('/api/memory/export')
}

export function getApprovals(): Promise<ApprovalRequest[]> {
  return req<ApprovalRequest[]>('/api/approvals')
}

export function approveRequest(id: string, remember: boolean): Promise<ApprovalDecision> {
  return req<ApprovalDecision>(`/api/approvals/${id}/approve`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ remember }),
  })
}

export function denyRequest(id: string, remember: boolean): Promise<ApprovalRequest> {
  return req<ApprovalRequest>(`/api/approvals/${id}/deny`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ remember }),
  })
}

export interface SecretDraft {
  name: string
  connector?: string
  value: string
  scopes?: string[]
}

export function getSecrets(): Promise<SecretView[]> {
  return req<SecretView[]>('/api/secrets')
}

export function createSecret(draft: SecretDraft): Promise<SecretView> {
  return req<SecretView>('/api/secrets', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(draft),
  })
}

export function deleteSecret(id: string): Promise<void> {
  return req<void>(`/api/secrets/${id}`, { method: 'DELETE' })
}

export function createWorkflow(draft: WorkflowDraft): Promise<WorkflowView> {
  return req<WorkflowView>('/api/workflows', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify(draft),
  })
}

export function runWorkflow(id: string): Promise<RunView> {
  return req<RunView>(`/api/workflows/${id}/run`, { method: 'POST', headers: jsonHeaders })
}

export function deleteWorkflow(id: string): Promise<void> {
  return req<void>(`/api/workflows/${id}`, { method: 'DELETE' })
}

export function getConnectors(): Promise<ConnectorInfo[]> {
  return req<ConnectorInfo[]>('/api/connectors')
}

export function invokeConnector(id: string, actionId: string): Promise<{ result: string }> {
  return req<{ result: string }>(`/api/connectors/${id}/actions/${actionId}`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ args: '{}' }),
  })
}

// --- Dashboard data sources -------------------------------------------------

/** Live system telemetry snapshot pushed over SSE and returned by /api/status. */
export interface MonitorSnapshot {
  os: string
  cpu: { availableProcessors: number; systemLoadAverage: number; processCpuLoad: number; systemCpuLoad: number }
  memory: { totalPhysicalBytes: number; freePhysicalBytes: number; usedPhysicalBytes: number }
  disk: { totalBytes: number; freeBytes: number; usableBytes: number }
  runtime: { activeAgents: number; runningTasks: number; registeredAgents: number; connectorHealth: string }
  jarvisHealth: string
}

/** Ask Jarvis (free text or a quick action) — full agent run with step trace. */
export function chat(message: string): Promise<ChatResponse> {
  return req<ChatResponse>('/api/chat', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ message, sessionId }),
  })
}

export function getTasks(limit = 20): Promise<TaskItem[]> {
  return req<TaskItem[]>(`/api/tasks?limit=${limit}`)
}

export function getAgents(): Promise<AgentDef[]> {
  return req<AgentDef[]>('/api/agents')
}

export function getMemoryList(query = ''): Promise<Memory[]> {
  return req<Memory[]>(`/api/memory?q=${encodeURIComponent(query)}`)
}

export function getAudit(limit = 20): Promise<AuditEntry[]> {
  return req<AuditEntry[]>(`/api/audit?limit=${limit}`)
}

export function getStatus(): Promise<MonitorSnapshot> {
  return req<MonitorSnapshot>('/api/status')
}

export function getRuns(limit = 50): Promise<RunRecord[]> {
  return req<RunRecord[]>(`/api/runs?limit=${limit}`)
}

export interface SettingsView {
  provider: string
  model: string
  hasAnthropicKey: boolean
  providers: string[]
}

export function getSettings(): Promise<SettingsView> {
  return req<SettingsView>('/api/settings')
}

export function setProvider(provider: string, model?: string): Promise<SettingsView> {
  return req<SettingsView>('/api/settings/provider', {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ provider, model }),
  })
}

/** Subscribe to the live monitor SSE stream; returns the EventSource so callers can close it. */
export function subscribeMonitor(onSnapshot: (s: MonitorSnapshot) => void): EventSource {
  const es = new EventSource('/api/monitor/stream')
  es.onmessage = (e) => {
    try {
      onSnapshot(JSON.parse(e.data) as MonitorSnapshot)
    } catch {
      /* ignore malformed frame */
    }
  }
  return es
}
