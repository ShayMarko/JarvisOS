import { friendlyError } from '../lib/format'

/** A friendly error card for non-technical users. */
export function ErrorCard({ message }: { message?: string }) {
  return (
    <div className="err-card">
      <span className="ic">⚠</span>
      <div><div className="t">Something went wrong</div><div className="m">{friendlyError(message)}</div></div>
    </div>
  )
}
