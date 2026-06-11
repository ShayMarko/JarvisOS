import { getConnectors } from '../../api'
import type { ConnectorInfo } from '../../api'
import { useFetch } from '../../lib/useFetch'

/** What each connector is for — shown so you know its purpose at a glance. Keyed by connector id. */
const PURPOSE: Record<string, string> = {
  ayrshare: 'Publish & schedule posts across your social accounts.',
  calcom: 'Scheduling links and bookings via Cal.com.',
  calendar: 'Read & create events on your Google Calendar.',
  cloudflare: 'DNS, CDN and edge hosting on Cloudflare.',
  etsy: 'List and manage products on your Etsy shop.',
  github: 'Repos, issues, pull requests and commits on GitHub.',
  gmail: 'Read, search and send mail from Gmail.',
  gdrive: 'Browse and manage files in Google Drive.',
  gumroad: 'Sell digital products and read sales on Gumroad.',
  lemonsqueezy: 'Sell products and read orders on Lemon Squeezy.',
  maps: 'Location, places and directions.',
  mongo: 'Query and write to your MongoDB database.',
  mysql: 'Query and write to your MySQL database.',
  netlify: 'Deploy and host sites on Netlify.',
  notion: 'Read and build pages & databases in Notion.',
  plausible: 'Privacy-friendly website analytics.',
  printful: 'Print-on-demand fulfilment for merch.',
  reddit: 'Read posts and trends from Reddit.',
  resend: 'Send transactional & marketing email.',
  rss: 'Pull headlines from RSS / news feeds.',
  shopify: 'Manage products and orders on your Shopify store.',
  slack: 'Post and read messages in Slack.',
  stripe: 'Payments, customers and revenue via Stripe.',
  telegram: 'Send & receive messages via your Telegram bot.',
  twilio: 'Send SMS / WhatsApp messages via Twilio.',
  youtube: 'Read videos, stats and transcripts from YouTube.',
}

const STATUS: Record<string, { cls: string; label: string }> = {
  CONNECTED: { cls: 'ok', label: 'Connected' },
  DISCONNECTED: { cls: 'warn', label: 'Not connected' },
  ERROR: { cls: 'bad', label: 'Error' },
}

/** How the credential is supplied — always the backend, never typed here. */
function credLabel(secret: string | null): string {
  if (!secret) return 'No credential required'
  if (secret.startsWith('oauth:')) return `🔐 ${secret.slice(6)} OAuth — set in backend`
  return `🔑 ${secret} — set in backend (application.yml)`
}

export function ConnectorsWindow() {
  const { data: conns, refresh } = useFetch(getConnectors, [])
  if (!conns) return <div className="w-empty"><span className="spin-fast">◠</span></div>

  const live = conns.filter((c) => c.status === 'CONNECTED').length

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        <span className="note">{live}/{conns.length} connected</span>
      </div>
      <div className="conn-note">🔒 Credentials are configured in the backend (application.yml / secrets vault) — never entered here.</div>
      <div className="conn-grid">
        {conns.map((c: ConnectorInfo) => {
          const st = STATUS[c.status] ?? STATUS.DISCONNECTED
          return (
            <div className={`conn-card ${st.cls}`} key={c.id}>
              <div className="conn-top">
                <span className={`conn-pill ${st.cls}`}><i className={`dot-s ${st.cls}`} />{st.label}</span>
                <span className="conn-cat">{c.category}</span>
              </div>
              <div className="conn-name">{c.name}</div>
              <div className="conn-desc">{PURPOSE[c.id] ?? `${c.category} connector.`}</div>
              <div className="conn-foot">
                <span className="conn-cred">{credLabel(c.requiredSecret)}</span>
                {c.actions.length > 0 && <span className="conn-acts">{c.actions.length} action{c.actions.length === 1 ? '' : 's'}</span>}
              </div>
            </div>
          )
        })}
      </div>
    </>
  )
}
