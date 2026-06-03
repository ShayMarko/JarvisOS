import type { ChatResponse, Step } from './api'

export type SphereKind = 'gyro' | 'orbital' | 'halo'

export type WinKind =
  | 'conversation' | 'today' | 'memory' | 'history' | 'settings' | 'agents' | 'logs'
  | 'files' | 'backups' | 'plugins' | 'tokens' | 'notifications' | 'result' | 'response'

/** A floating HUD window instance. */
export interface Win {
  key: string
  kind: WinKind
  title: string
  subtitle: string
  dim: string
  x: number
  y: number
  z: number
  payload?: unknown
}

/** One exchange in the running conversation transcript. */
export interface Turn {
  id: string
  prompt: string
  loading: boolean
  steps: Step[]
  startedAt?: number   // epoch ms when the turn began — drives the live elapsed timer
  resp?: ChatResponse
  commandResult?: { status?: string; message?: string; data?: unknown }
}
