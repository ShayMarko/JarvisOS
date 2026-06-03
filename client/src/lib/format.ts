/* Pure formatting / parsing / classification helpers — no React, no DOM. Unit-tested. */
import type { Step } from '../api'

// --- numbers / time ---------------------------------------------------------------------------
export function pct(v?: number) {
  return v === undefined || v === null || v < 0 || Number.isNaN(v) ? 0 : Math.round(v * 100)
}
export function gb(b?: number) { return !b ? '0' : (b / 1024 ** 3).toFixed(1) }
export function ago(iso?: string | null) {
  if (!iso) return ''
  const d = Date.parse(iso); if (Number.isNaN(d)) return ''
  const s = Math.max(0, (Date.now() - d) / 1000)
  if (s < 60) return `${Math.floor(s)}s`
  if (s < 3600) return `${Math.floor(s / 60)}m`
  if (s < 86400) return `${Math.floor(s / 3600)}h`
  return `${Math.floor(s / 86400)}d`
}
export function kb(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
/** Format a per-second throughput for the telemetry readout. */
export function rate(bytesPerSec?: number): string {
  if (!bytesPerSec || bytesPerSec < 1) return '0'
  if (bytesPerSec < 1024) return `${Math.round(bytesPerSec)} B/s`
  if (bytesPerSec < 1024 * 1024) return `${(bytesPerSec / 1024).toFixed(0)} KB/s`
  return `${(bytesPerSec / 1024 / 1024).toFixed(1)} MB/s`
}

// --- cache flagging: the backend marks a cached answer with model "cache:<ageSeconds>" --------
export function cacheAgeSeconds(model?: string): number | null {
  if (!model || !model.startsWith('cache')) return null
  const n = parseInt(model.split(':')[1] ?? '0', 10)
  return Number.isFinite(n) ? n : 0
}
export function fmtAge(s: number): string {
  if (s < 60) return `${s}s`
  if (s < 3600) return `${Math.round(s / 60)} min`
  return `${Math.round(s / 3600)} hr`
}

// --- runs / steps -----------------------------------------------------------------------------
export function runState(status: string): 'success' | 'failed' | 'running' {
  const s = status?.toUpperCase()
  if (s === 'ERROR' || s === 'FAILED') return 'failed'
  if (s === 'RUNNING' || s === 'PENDING') return 'running'
  return 'success'
}
export function parseSteps(json: string | null): Step[] {
  if (!json) return []
  try { const v = JSON.parse(json); return Array.isArray(v) ? v as Step[] : [] } catch { return [] }
}

// --- files ------------------------------------------------------------------------------------
export const IMG_EXT = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg', 'ico']
export const VID_EXT = ['mp4', 'webm', 'm4v', 'ogv', 'mov']
export const AUD_EXT = ['mp3', 'wav', 'm4a', 'aac', 'flac', 'ogg', 'oga']
export const TEXT_EXT = ['txt', 'md', 'markdown', 'json', 'jsonl', 'xml', 'yaml', 'yml', 'csv', 'tsv', 'log',
  'js', 'jsx', 'ts', 'tsx', 'java', 'kt', 'py', 'rb', 'go', 'rs', 'c', 'h', 'cpp', 'hpp', 'cs', 'php',
  'swift', 'sh', 'bash', 'zsh', 'sql', 'html', 'htm', 'css', 'scss', 'less', 'toml', 'ini', 'cfg',
  'conf', 'env', 'properties', 'gradle', 'dockerfile', 'gitignore', 'editorconfig', 'svg']
export function extOf(name: string): string { return name.includes('.') ? name.split('.').pop()!.toLowerCase() : '' }
export function rawUrl(path: string): string { return `/api/files/raw?path=${encodeURIComponent(path)}` }
/** How to render a file in the viewer. */
export function fileKind(name: string): 'image' | 'video' | 'audio' | 'pdf' | 'text' | 'binary' {
  const e = extOf(name)
  if (IMG_EXT.includes(e)) return 'image'
  if (VID_EXT.includes(e)) return 'video'
  if (AUD_EXT.includes(e)) return 'audio'
  if (e === 'pdf') return 'pdf'
  if (e === '' || TEXT_EXT.includes(e)) return 'text'   // no-extension files: try as text
  return 'binary'
}

// --- safety / friendliness for any text shown to the user -------------------------------------
/** A model that emitted a raw tool-call/bean dump as text instead of using the tool channel. */
export function looksLikeRawTool(s?: string): boolean {
  if (!s) return false
  const t = s.trim()
  if (/"nodeType"\s*:\s*"OBJECT"|"bigDecimal"\s*:|"valueNode"\s*:/.test(t)) return true
  return t.startsWith('{') && /"(name|tool|function)"\s*:/.test(t) && /"(parameters|arguments)"\s*:/.test(t)
}
/** True when a string is actually an HTML page / markup that leaked through. */
export function isHtmlish(s?: string): boolean {
  if (!s) return false
  return /<!doctype|<html|<\/?[a-z]+[^>]*>/i.test(s) && (s.match(/<[^>]+>/g)?.length ?? 0) > 2
}
/** Strip tags/entities and collapse whitespace — used to tidy error text for humans. */
export function stripHtml(s: string): string {
  const t = s.replace(/<[^>]*>/g, ' ').replace(/&[a-z#0-9]+;/gi, ' ').replace(/\s+/g, ' ').trim()
  return t.length > 200 ? t.slice(0, 200) + '…' : t
}
/** A friendly, human message for an error string (no codes, no HTML, no jargon). */
export function friendlyError(raw?: string): string {
  if (!raw) return 'Something went wrong. Please try again.'
  if (isHtmlish(raw)) return 'The service returned an unexpected response. Please try again in a moment.'
  const r = raw.replace(/^Error:\s*/i, '').trim()
  if (/403|forbidden|declined/i.test(r)) return 'A service declined the request — it may be rate-limiting us. Please try again shortly.'
  if (/timeout|timed out/i.test(r)) return 'That took too long and timed out. Please try again.'
  if (/connect|refused|unreachable|unavailable/i.test(r)) return "I couldn't reach that service. It may be offline — please try again."
  if (/permission|denied|blocked/i.test(r)) return r // policy/permission messages are already user-facing
  return r.length > 200 ? r.slice(0, 200) + '…' : r
}
