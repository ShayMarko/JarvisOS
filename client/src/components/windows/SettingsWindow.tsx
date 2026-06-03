import { useEffect, useState } from 'react'
import { getSettings, setBudget as apiSetBudget, setProvider as apiSetProvider } from '../../api'
import type { SettingsView } from '../../api'
import { pref, savePref } from '../../lib/prefs'

export function SettingsWindow() {
  const [settings, setSettings] = useState<SettingsView | null>(null)
  const [provider, setProviderState] = useState(pref('jarvis.provider', 'mock'))
  const [model, setModel] = useState('')
  const [stt, setStt] = useState(pref('jarvis.stt', 'whisper'))
  const [tts, setTts] = useState(pref('jarvis.tts', 'system'))
  const [lang, setLang] = useState(pref('jarvis.lang', 'en-US'))
  const [voice, setVoice] = useState(pref('jarvis.voice', 'natural'))
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  // Each provider keeps its own model field (openai-model vs ollama-model vs Anthropic model).
  const modelFor = (s: SettingsView) => s.provider === 'openai' ? (s.openaiModel ?? '')
    : s.provider === 'ollama' ? (s.ollamaModel ?? '') : s.model

  useEffect(() => { getSettings().then((s) => { setSettings(s); setProviderState(s.provider); setModel(modelFor(s)) }).catch(() => {}) }, [])

  const changeProvider = (p: string) => {
    setProviderState(p); savePref('jarvis.provider', p); setSaving(true)
    // Switch provider only — don't overwrite the new provider's model with the old one's value.
    apiSetProvider(p).then((s) => { setSettings(s); setProviderState(s.provider); setModel(modelFor(s)) }).catch(() => {}).finally(() => setSaving(false))
  }

  // Settings apply on change; Save re-confirms provider+model and flashes a confirmation.
  const saveAll = () => {
    setSaving(true)
    apiSetProvider(provider, model || undefined)
      .then((s) => { setSettings(s); setModel(modelFor(s)); setSaved(true); setTimeout(() => setSaved(false), 2000) })
      .catch(() => {})
      .finally(() => setSaving(false))
  }
  const providerLabel = (p?: string) => p === 'claude' ? 'Anthropic' : p === 'ollama' ? 'Ollama' : p === 'openai' ? 'OpenAI' : 'Mock'

  return (
    <>
      <div className="cards3">
        <div className="scard"><div className="lbl">AI PROVIDER</div><div className="big">{providerLabel(settings?.provider ?? provider)}</div><div className="sub">{(settings?.provider ?? provider) === 'ollama' ? 'local · ' + (settings?.ollamaModel ?? 'ollama') : settings?.hasAnthropicKey ? 'API key set' : 'no key · offline'}</div></div>
        <div className="scard"><div className="lbl">STT ENGINE</div><div className="big">{stt === 'whisper' ? 'Whisper' : 'Local'}</div><div className="sub">{stt === 'whisper' ? 'cloud · accurate' : 'offline'}</div></div>
        <div className="scard"><div className="lbl">TTS ENGINE</div><div className="big">{tts === 'system' ? 'System' : 'ElevenLabs'}</div><div className="sub">{tts === 'system' ? 'native · fastest' : 'cloud'}</div></div>
      </div>

      <div className="field"><label>AI provider {saving && <span className="spin-fast">◠</span>}</label>
        <select value={provider} onChange={(e) => changeProvider(e.target.value)}>
          <option value="ollama">Ollama (local · agentic · no key)</option>
          <option value="openai">OpenAI (cloud · needs key)</option>
          <option value="claude">Anthropic Claude (cloud · needs key)</option>
          <option value="mock">Mock (offline stub · scripted)</option>
        </select>
        <div className="note">{provider === 'claude' && !settings?.hasAnthropicKey ? 'No ANTHROPIC_API_KEY set — calls fall back to the offline mock.' : provider === 'openai' && !settings?.hasOpenaiKey ? 'No OPENAI_API_KEY set — calls fall back to the offline mock.' : provider === 'ollama' ? 'Uses your local Ollama; falls back to the mock if it is not running.' : 'Takes effect immediately for new requests.'}</div></div>
      <div className="field"><label>Model</label>
        <input value={model} onChange={(e) => setModel(e.target.value)} onBlur={() => apiSetProvider(provider, model).then(setSettings).catch(() => {})} placeholder="qwen2.5-coder:14b" /></div>

      <div className="field"><label>Daily token budget — paid providers only (0 = unlimited) {settings?.budget?.paused && <span style={{ color: 'var(--bad)' }}>· PAUSED</span>}</label>
        <input type="number" min={0} step={10000} defaultValue={settings?.budget?.dailyTokenBudget ?? 0}
          onBlur={(e) => apiSetBudget({ dailyTokenBudget: Math.max(0, Number(e.target.value) || 0) }).then(setSettings).catch(() => {})}
          placeholder="0" />
        <div className="note">
          {settings?.budget
            ? `Used today: ${settings.budget.tokensToday.toLocaleString()} tokens` + (settings.budget.dailyTokenBudget > 0 ? ` · ${settings.budget.remaining.toLocaleString()} left` : ' · no cap')
            : 'Caps Claude/OpenAI spend per day. Ollama & Mock are free and never metered.'}
        </div>
        <div className="seg" style={{ marginTop: 8 }}>
          <button className={!settings?.budget?.paused ? 'on' : ''} onClick={() => apiSetBudget({ paused: false }).then(setSettings).catch(() => {})}><span className="pip" />AI on</button>
          <button className={settings?.budget?.paused ? 'on' : ''} onClick={() => apiSetBudget({ paused: true }).then(setSettings).catch(() => {})}><span className="pip" />Pause (kill-switch)</button>
        </div></div>

      <div className="field"><label>Speech-to-text engine</label>
        <select value={stt} onChange={(e) => { setStt(e.target.value); savePref('jarvis.stt', e.target.value) }}>
          <option value="whisper">OpenAI Whisper (cloud · higher accuracy)</option><option value="browser">Browser / local (offline)</option></select>
        <div className="note">The live mic uses the browser's recognizer today; Whisper is the upgrade path.</div></div>
      <div className="field"><label>Text-to-speech engine</label>
        <select value={tts} onChange={(e) => { setTts(e.target.value); savePref('jarvis.tts', e.target.value) }}>
          <option value="system">System TTS (native · fastest)</option><option value="eleven">ElevenLabs (cloud)</option></select></div>
      <div className="field"><label>Voice language</label>
        <select value={lang} onChange={(e) => { setLang(e.target.value); savePref('jarvis.lang', e.target.value) }}>
          <option value="en-US">English (US)</option><option value="en-GB">English (UK)</option><option value="he-IL">Hebrew</option></select></div>
      <div className="field"><label>Voice type</label>
        <div className="seg">
          <button className={voice === 'natural' ? 'on' : ''} onClick={() => { setVoice('natural'); savePref('jarvis.voice', 'natural') }}><span className="pip" />Natural</button>
          <button className={voice === 'robotic' ? 'on' : ''} onClick={() => { setVoice('robotic'); savePref('jarvis.voice', 'robotic') }}><span className="pip" />Robotic</button>
        </div></div>

      <div className="settings-foot">
        <span className="note" style={{ flex: 1 }}>Settings apply as you change them. Save re-confirms the AI provider &amp; model.</span>
        <button className="hint primary" onClick={saveAll} disabled={saving}>{saving ? 'Saving…' : saved ? 'Saved ✓' : 'Save'}</button>
      </div>
    </>
  )
}
