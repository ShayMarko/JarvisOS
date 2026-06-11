import type { ChatResponse, Step } from './api'

export type SphereKind = 'gyro' | 'orbital' | 'halo'

export type WinKind =
  | 'conversation' | 'activity' | 'settings' | 'capabilities' | 'logs'
  | 'files' | 'backups' | 'usage' | 'approvals'
  | 'undo' | 'vision' | 'discord' | 'notifications' | 'result' | 'response'

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
  /** True when collapsed to the window dock (hidden from the canvas but still open). */
  minimized?: boolean
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
  /** Set when this turn is a window collapsed back into the chat — the "open in window ↗" chip reopens it. */
  reopen?: { kind: WinKind; payload?: unknown }
}
