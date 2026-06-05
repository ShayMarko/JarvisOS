import { useState } from 'react'
import { getDiscordStatus, testDiscord } from '../../api'
import { useFetch } from '../../lib/useFetch'

const CONFIG_KEYS = [
  ['JARVIS_DISCORD_ENABLED', 'true'],
  ['JARVIS_DISCORD_BOT_TOKEN', '<your bot token>'],
  ['JARVIS_DISCORD_CHANNEL_ID', '<your private channel id>'],
]

export function DiscordWindow() {
  const { data: status, refresh } = useFetch(getDiscordStatus)
  const [msg, setMsg] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const ping = () => {
    setBusy(true); setMsg(null)
    testDiscord().then((r) => setMsg(r.message)).catch((e) => setMsg((e as Error).message)).finally(() => setBusy(false))
  }

  if (!status) return <div className="w-empty"><span className="spin-fast">◠</span></div>

  const dot = (ok: boolean) => <span className={`dot-s ${ok ? 'ok' : 'warn'}`} />
  const row = (label: string, ok: boolean, note: string) => (
    <div className="row" key={label}>
      {dot(ok)}<span className="grow">{label}</span><span className="when">{ok ? note : 'no'}</span>
    </div>
  )

  return (
    <>
      <div className="files-bar">
        <button className="hint" onClick={refresh}>⟳ Refresh</button>
        <span className="grow" />
        <button className="hint" disabled={busy || !status.pushReady} onClick={ping}>{busy ? '…' : 'Send test message'}</button>
      </div>

      <div className="rows">
        {row('Enabled', status.enabled, 'on')}
        {row('Bot token set', status.tokenSet, 'yes')}
        {row('Channel id set', status.channelSet, 'yes')}
        {row('Connected (active)', status.active, 'live')}
        {row('Push / briefing ready', status.pushReady, 'ready')}
      </div>

      {msg && <div className="appr-desc" style={{ padding: '8px 4px' }}>{msg}</div>}

      <div className="dv-sec-title" style={{ marginTop: 14 }}>How to configure</div>
      <div className="appr-desc">
        Discord is configured on the box (it connects at startup). Set these in your environment / config,
        then restart Jarvis:
      </div>
      <pre className="appr-preview">{CONFIG_KEYS.map(([k, v]) => `${k}=${v}`).join('\n')}</pre>
      <div className="appr-desc" style={{ fontSize: 11 }}>
        Create a bot at the Discord Developer Portal → add it to your private server → copy the bot token and
        the channel id. The bot needs the <b>View Channels</b> + <b>Send/Read Messages</b> permissions.
      </div>
    </>
  )
}
