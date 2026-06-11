import { useCallback, useEffect, useRef, useState } from 'react'
import {
  fetchCommands, getConversation, getNotifications, getSettings, getStatus, getUnreadCount,
  markNotificationRead, setProvider as apiSetProvider, streamInput,
} from './api'
import type {
  ChatResponse, CommandDefinition, CommandResult, MonitorSnapshot, NotificationItem, SettingsView, Step,
} from './api'
import type { Turn, Win, WinKind } from './types'
import { ago, isHtmlish, looksLikeRawTool } from './lib/format'
import { pref } from './lib/prefs'
import { SR, speak, speakSmart, stopSpeaking, takeLongSpeech, wantsContinue } from './lib/voice'
import { SLASH_TAB, SLASH_WINDOW, WIN_META, matchWindowOpen } from './lib/windows'
import { ApprovalActions } from './components/ApprovalActions'
import { CommandPalette } from './components/CommandPalette'
import { FloatingWindow } from './components/FloatingWindow'
import { MvpShell } from './components/MvpShell'
import { WindowBody } from './components/WindowBody'

/* eslint-disable @typescript-eslint/no-explicit-any */

export default function App() {
  const [snap, setSnap] = useState<MonitorSnapshot | null>(null)
  const [commands, setCommands] = useState<CommandDefinition[]>([])
  const [unread, setUnread] = useState(0)
  const [settings, setSettings] = useState<SettingsView | null>(null)
  const [busy, setBusy] = useState(false)
  const [input, setInput] = useState('')
  const [cmdkOpen, setCmdkOpen] = useState(false)
  const [wins, setWins] = useState<Win[]>([])
  const [turns, setTurns] = useState<Turn[]>([])
  const [listening, setListening] = useState(false)
  const [wake, setWake] = useState(false)
  const [notifOpen, setNotifOpen] = useState(false)
  const [notifItems, setNotifItems] = useState<NotificationItem[]>([])
  const [notifExpanded, setNotifExpanded] = useState<string | null>(null)
  const zRef = useRef(30)
  const wakeRef = useRef<any>(null)
  const pttRef = useRef<any>(null)
  const pttTextRef = useRef('')
  const esRef = useRef<EventSource | null>(null)   // current streamInput connection
  const ttsOn = pref('jarvis.tts', 'system') !== 'off'

  useEffect(() => {
    fetchCommands().then(setCommands).catch(() => {})
    getSettings().then(setSettings).catch(() => {})
    // Rehydrate the conversation transcript for this session (survives reloads).
    getConversation().then((hist) => {
      const seeded: Turn[] = []
      hist.forEach((h, i) => {
        if (h.role === 'USER') {
          seeded.push({ id: `h-${i}`, prompt: h.content, loading: false, steps: [] })
        } else if (seeded.length > 0 && !seeded[seeded.length - 1].resp && !seeded[seeded.length - 1].commandResult) {
          seeded[seeded.length - 1].commandResult = { status: 'OK', message: h.content }
        }
      })
      if (seeded.length > 0) setTurns(seeded)
    }).catch(() => {})
    const poll = () => { getStatus().then(setSnap).catch(() => {}); getUnreadCount().then(setUnread).catch(() => {}) }
    poll()
    const s = setInterval(poll, 3000)
    const k = (e: KeyboardEvent) => { if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setCmdkOpen((o) => !o) } }
    window.addEventListener('keydown', k)
    return () => { clearInterval(s); window.removeEventListener('keydown', k); esRef.current?.close() }
  }, [])

  const focusWin = useCallback((key: string) => setWins((ws) => ws.map((w) => w.key === key ? { ...w, z: ++zRef.current } : w)), [])
  const closeWin = useCallback((key: string) => setWins((ws) => ws.filter((w) => w.key !== key)), [])
  const minimizeWin = useCallback((key: string) => setWins((ws) => ws.map((w) => w.key === key ? { ...w, minimized: true } : w)), [])
  const restoreWin = useCallback((key: string) =>
    setWins((ws) => ws.map((w) => w.key === key ? { ...w, minimized: false, z: ++zRef.current } : w)), [])

  /** Cascade or tile the OPEN (non-minimized) windows — "tidy the desk" without manual dragging. */
  const arrangeWindows = useCallback((mode: 'cascade' | 'tile') => {
    setWins((ws) => {
      const open = ws.filter((w) => !w.minimized)
      const cols = Math.max(1, Math.ceil(Math.sqrt(open.length)))
      const rows = Math.max(1, Math.ceil(open.length / cols))
      const pad = 16
      const topBar = 96
      const bottomBar = 150
      const cw = Math.floor((window.innerWidth - pad * (cols + 1)) / cols)
      const ch = Math.floor((window.innerHeight - topBar - bottomBar - pad * (rows - 1)) / rows)
      let i = -1
      return ws.map((w) => {
        if (w.minimized) return w
        i++
        if (mode === 'cascade') {
          return { ...w, x: 120 + i * 34, y: 110 + i * 30, z: ++zRef.current }
        }
        const c = i % cols
        const r = Math.floor(i / cols)
        return {
          ...w,
          x: pad + c * (cw + pad),
          y: topBar + r * (ch + pad),
          dim: `${Math.max(360, cw)}×${Math.max(220, ch)}`,
          z: ++zRef.current,
        }
      })
    })
  }, [])

  /** Alt-Tab style: bring the least-recently-focused open window to the front (cycles through them). */
  const cycleWindows = useCallback(() => {
    setWins((ws) => {
      const open = ws.filter((w) => !w.minimized)
      if (open.length < 2) {
        if (open.length === 1) return ws.map((w) => w.key === open[0].key ? { ...w, z: ++zRef.current } : w)
        return ws
      }
      const target = open.reduce((lo, w) => (w.z < lo.z ? w : lo), open[0])   // lowest z = furthest back
      return ws.map((w) => w.key === target.key ? { ...w, z: ++zRef.current } : w)
    })
  }, [])

  const openWindow = useCallback((kind: WinKind, payload?: unknown, titleOverride?: string, pos?: { x: number; y: number }) => {
    const meta = WIN_META[kind]
    const singleton = kind !== 'result' && kind !== 'response'
    setWins((ws) => {
      if (singleton) {
        const existing = ws.find((w) => w.kind === kind)
        // Re-focus an already-open singleton — but if an anchor was given (e.g. opened from the bell),
        // also snap it back under that anchor so it reappears where you expect.
        if (existing) return ws.map((w) => w.key === existing.key ? { ...w, z: ++zRef.current, ...(pos ?? {}) } : w)
      }
      const n = ws.length
      const key = singleton ? kind : `${kind}-${zRef.current}`
      const win: Win = {
        key, kind, title: titleOverride ?? meta.title, subtitle: meta.subtitle, dim: meta.dim,
        x: pos?.x ?? 320 + (n % 4) * 36, y: pos?.y ?? 150 + (n % 4) * 30, z: ++zRef.current, payload,
      }
      return [...ws, win]
    })
    return singleton ? kind : `${kind}-${zRef.current}`
  }, [])

  // Open a window anchored under a clicked control (e.g. the notification bell): right-aligned to the
  // anchor, just below it, clamped on-screen. It stays fully draggable/resizable afterward.
  const openWindowUnder = useCallback((kind: WinKind, anchor: DOMRect) => {
    const [w] = (WIN_META[kind].dim || '520×520').split('×').map(Number)
    const width = w || 520
    const x = Math.max(12, Math.min(window.innerWidth - width - 12, anchor.right - width))
    const y = Math.min(window.innerHeight - 120, anchor.bottom + 10)
    openWindow(kind, undefined, undefined, { x, y })
  }, [openWindow])

  // Reverse of "open in window ↗": collapse a floating window back into the chat as an inline turn.
  // Data windows (result/response) bring their content with them; tool windows leave a breadcrumb.
  // Either way a "open in window ↗" chip on the turn reopens the exact window.
  const collapseToChat = useCallback((win: Win) => {
    const p = win.payload as { status?: string; message?: string; data?: unknown } | undefined
    const isData = win.kind === 'result' || win.kind === 'response'
    const cr = isData
      ? { status: p?.status ?? 'OK', message: p?.message ?? win.title, data: p?.data }
      : { status: 'OK', message: `${win.title} — moved back to chat.` }
    setTurns((ts) => [...ts, {
      id: `clps-${Date.now()}-${ts.length}`,
      prompt: '', loading: false, steps: [],
      commandResult: cr,
      reopen: { kind: win.kind, payload: win.payload },
    }])
    closeWin(win.key)
  }, [closeWin])

  // Cmd-` / Ctrl-` — Alt-Tab style window cycling.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === '`') { e.preventDefault(); cycleWindows() }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [cycleWindows])

  const updateTurn = useCallback((id: string, patch: Partial<Turn>) =>
    setTurns((ts) => ts.map((t) => t.id === id ? { ...t, ...patch } : t)), [])

  // Single cognitive door: raw input → backend InputRouter → command or streamed Brain.
  // All exchanges accumulate in ONE Conversation window (a running transcript).
  const askInput = useCallback((raw: string, spoken = false) => {
    setBusy(true)
    esRef.current?.close()                  // close any prior stream before starting a new one
    const id = `t-${++zRef.current}`
    setTurns((ts) => [...ts, { id, prompt: raw, loading: true, steps: [], startedAt: Date.now() }])
    const collected: Step[] = []
    esRef.current = streamInput(raw, {
      onStep: (s) => { collected.push(s); updateTurn(id, { steps: [...collected] }) },
      onResult: (result: CommandResult) => {
        if (result.type === 'chat') {
          const resp = result.data as ChatResponse
          updateTurn(id, { loading: false, steps: [...collected], resp })
          if (spoken && ttsOn && !isHtmlish(resp.answer) && !looksLikeRawTool(resp.answer)) speakSmart(resp.answer)
        } else {
          updateTurn(id, { loading: false, steps: [...collected], commandResult: { status: result.status, message: result.message, data: result.data } })
          if (spoken && ttsOn && result.status !== 'ERROR' && result.message) speakSmart(result.message)
        }
      },
      onError: (m) => updateTurn(id, { loading: false, steps: [...collected], commandResult: { status: 'ERROR', message: m } }),
      onDone: () => setBusy(false),
    })
  }, [openWindow, updateTurn, ttsOn])

  const runCmd = useCallback((slash: string) => {
    setCmdkOpen(false)
    const slash0 = slash.split(' ')[0]
    const win = SLASH_WINDOW[slash0]
    if (win) { openWindow(win, SLASH_TAB[slash0]); return }
    // Non-window commands answer inline in the chat (same cognitive door as typing them),
    // instead of popping a separate Result window. You can still promote any result to a window.
    askInput(slash)
  }, [openWindow, askInput])

  const submit = useCallback((text: string, spoken = false) => {
    const t = text.trim(); if (!t) return
    setInput('')
    // Barge-in: any new input stops Jarvis mid-sentence (he was talking, you took over).
    stopSpeaking()
    // "continue / keep going / tell me more" → speak the full version of the last shortened answer.
    // Pure voice-control, handled client-side (no AI round trip).
    if (wantsContinue(t)) {
      const full = takeLongSpeech()
      if (full) { if (ttsOn) speak(full); return }
    }
    // Window-opener slashes are pure UI nav → open instantly, no round trip.
    if (t.startsWith('/') && SLASH_WINDOW[t.split(' ')[0]]) { openWindow(SLASH_WINDOW[t.split(' ')[0]], SLASH_TAB[t.split(' ')[0]]); return }
    // Natural-language UI navigation ("open/show the X window") — opening a window is a UI
    // action, not an AI task, so handle it client-side instead of routing to the Brain.
    const nav = matchWindowOpen(t)
    if (nav) { openWindow(nav); return }
    // Everything else (free text + non-window commands) goes through the one cognitive door.
    askInput(t, spoken)
  }, [askInput, openWindow, ttsOn])

  // --- Voice -------------------------------------------------------------
  // Mic toggle: click once to start listening, click again to stop & send to Jarvis.
  const startPTT = useCallback(() => {
    if (pttRef.current) {                       // already listening → stop; onend sends
      const r = pttRef.current; pttRef.current = null
      try { r.stop() } catch { /* ignore */ }
      return
    }
    if (!SR) { alert('Voice input needs a Chromium-based browser (Web Speech API).'); return }
    stopSpeaking()                              // barge-in: cut off whatever Jarvis is saying
    const r = new SR(); r.lang = pref('jarvis.lang', 'en-US'); r.continuous = true; r.interimResults = true
    pttTextRef.current = ''
    r.onresult = (e: any) => {
      let finalText = ''
      for (let i = 0; i < e.results.length; i++) if (e.results[i].isFinal) finalText += e.results[i][0].transcript + ' '
      if (finalText) pttTextRef.current = finalText
    }
    r.onerror = () => { pttRef.current = null; setListening(false) }
    r.onend = () => {
      pttRef.current = null; setListening(false)
      const t = pttTextRef.current.trim(); pttTextRef.current = ''
      if (t) submit(t, true)
    }
    try { r.start(); pttRef.current = r; setListening(true) } catch { setListening(false) }
  }, [submit])

  const toggleWake = useCallback(() => {
    if (wakeRef.current) { wakeRef.current.stop?.(); wakeRef.current = null; setWake(false); return }
    if (!SR) { alert('Wake-word needs a Chromium-based browser (Web Speech API).'); return }
    const r = new SR(); r.lang = pref('jarvis.lang', 'en-US'); r.continuous = true; r.interimResults = false
    r.onresult = (e: any) => {
      const t = (e.results[e.results.length - 1][0].transcript as string).toLowerCase()
      const i = t.indexOf('jarvis')
      if (i >= 0) { const cmd = t.slice(i + 6).trim(); if (cmd.length > 1) submit(cmd, true) }
    }
    r.onend = () => { if (wakeRef.current) { try { r.start() } catch { /* restart race */ } } }
    r.onerror = () => {}
    try { r.start(); wakeRef.current = r; setWake(true) } catch { setWake(false) }
  }, [submit])

  const uiProvider = settings ? (settings.provider === 'claude' || settings.provider === 'anthropic' ? 'anthropic' : settings.provider) : 'ollama'
  const pickProvider = useCallback((ui: string) => {
    const backend = ui === 'anthropic' ? 'claude' : ui   // UI label "anthropic" → backend provider "claude"
    apiSetProvider(backend).then(setSettings).catch(() => {})
  }, [])

  // Expand a notification to read it fully; mark just that one read on first open.
  const toggleNotif = useCallback((n: NotificationItem) => {
    setNotifExpanded((id) => (id === n.id ? null : n.id))
    if (!n.read) {
      markNotificationRead(n.id).catch(() => {})
      setNotifItems((items) => items.map((x) => (x.id === n.id ? { ...x, read: true } : x)))
      setUnread((u) => Math.max(0, u - 1))
    }
  }, [])

  return (
    <>
      {/* MVP main shell — organized menu + chat + neural-brain stage (opens existing windows) */}
      <MvpShell
        snap={snap} settings={settings} turns={turns} input={input} busy={busy}
        listening={listening} unread={unread} wins={wins}
        onInput={setInput} onSubmit={submit} onMic={startPTT}
        onOpen={(k, p) => openWindow(k, p)}
        onOpenUnder={openWindowUnder}
        onBell={() => { setNotifExpanded(null); getNotifications().then(setNotifItems).catch(() => {}); setNotifOpen(true) }}
        onFocusWin={(key) => { const w = wins.find((x) => x.key === key); if (w?.minimized) restoreWin(key); else focusWin(key) }}
        onCmdk={() => setCmdkOpen(true)}
        wake={wake} onToggleWake={toggleWake}
        uiProvider={uiProvider} onProvider={pickProvider}
      />

      {/* floating windows */}
      <div className="winlayer">
        {wins.filter((w) => !w.minimized).map((w) => (
          <FloatingWindow key={w.key} win={w} onClose={() => closeWin(w.key)} onFocus={() => focusWin(w.key)} onMinimize={() => minimizeWin(w.key)} onCollapse={() => collapseToChat(w)}>
            <WindowBody win={w} />
          </FloatingWindow>
        ))}
      </div>

      {/* Window dock — manage/switch open windows without cluttering the canvas (⌘` to cycle). */}
      {wins.length > 0 && (
        <div className="wm-dock">
          {wins.slice().sort((a, b) => a.title.localeCompare(b.title)).map((w) => (
            <button key={w.key} className={`wm-chip${w.minimized ? ' min' : ''}`}
              title={w.minimized ? 'Restore' : 'Bring to front'}
              onClick={() => (w.minimized ? restoreWin(w.key) : focusWin(w.key))}
              onDoubleClick={() => minimizeWin(w.key)}>
              {w.title}
            </button>
          ))}
          {wins.filter((w) => !w.minimized).length > 1 && (
            <span className="wm-tools">
              <button className="wm-tool" title="Tile windows" onClick={() => arrangeWindows('tile')}>▦</button>
              <button className="wm-tool" title="Cascade windows" onClick={() => arrangeWindows('cascade')}>▤</button>
            </span>
          )}
        </div>
      )}

      {/* notifications popover (anchored under the bell) */}
      {notifOpen && (
        <>
          <div className="notif-overlay" onClick={() => setNotifOpen(false)} />
          <div className="notif-pop">
            <div className="head"><span className="t">Notifications</span>{notifItems.length > 0 && <span className="mono" style={{ fontSize: 10, color: 'var(--muted)' }}>{notifItems.length}</span>}</div>
            <div className="list">
              {notifItems.length === 0
                ? <div className="empty">Nothing yet — you’re all caught up.</div>
                : notifItems.slice(0, 8).map((n) => {
                    const open = notifExpanded === n.id
                    return (
                      <div key={n.id}>
                        <button className={`item ${n.read ? 'read' : 'unread'}`} onClick={() => toggleNotif(n)}>
                          <span className={`dot-s ${n.type === 'error' ? 'bad' : n.type === 'warning' ? 'warn' : 'ok'}`} style={{ marginTop: 5 }} />
                          <span className="body"><div className="ttl">{n.title}{n.risk && <span className={`risk-badge r-${n.risk.toLowerCase()}`}>{n.risk}</span>}</div>{!open && n.body && <div className="sub">{n.body}</div>}</span>
                          <span className="when">{ago(n.createdAt)}</span>
                        </button>
                        {n.source === 'approval' && n.actionId && (
                          <ApprovalActions id={n.actionId} onDone={() => {
                            getNotifications().then(setNotifItems).catch(() => {})
                            getUnreadCount().then(setUnread).catch(() => {})
                          }} />
                        )}
                        {open && (
                          <div className="detail">
                            {n.body && <div>{n.body}</div>}
                            <div className="meta">
                              <span>{n.type}</span>
                              {n.source && <span>via {n.source}</span>}
                              <span>{new Date(n.createdAt).toLocaleString()}</span>
                            </div>
                          </div>
                        )}
                      </div>
                    )
                  })}
            </div>
            <div className="foot"><button className="showall" onClick={() => { setNotifOpen(false); openWindow('notifications') }}>SHOW ALL →</button></div>
          </div>
        </>
      )}

      {cmdkOpen && <CommandPalette commands={commands} onRun={runCmd} onClose={() => setCmdkOpen(false)} />}
    </>
  )
}
