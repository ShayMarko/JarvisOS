import { useEffect, useRef } from 'react'
import type { Turn } from '../../types'
import { cacheAgeSeconds, fmtAge, isHtmlish, looksLikeRawTool, stripHtml } from '../../lib/format'
import { DataView } from '../DataView'
import { ErrorCard } from '../ErrorCard'
import { LiveProgress } from '../LiveProgress'

/** The running chat transcript: one reused window, every exchange appended. */
export function ConversationWindow({ turns, onClear }: { turns: Turn[]; onClear: () => void }) {
  const endRef = useRef<HTMLDivElement | null>(null)
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [turns])
  return (
    <div className="convo">
      <div className="convo-bar">
        <span className="grow" style={{ color: 'var(--muted)', fontSize: 12 }}>{turns.length} exchange{turns.length === 1 ? '' : 's'}</span>
        {turns.length > 0 && <button className="hint" onClick={onClear}>Clear view</button>}
      </div>
      <div className="convo-scroll">
        {turns.length === 0
          ? <div className="w-empty"><div className="big">💬</div><div className="s">Ask Jarvis anything from the bar below — your whole conversation shows up here.</div></div>
          : turns.map((t) => (
            <div className="turn" key={t.id}>
              <div className="bubble user"><span className="who">You</span><div className="txt">{t.prompt}</div></div>
              <div className="bubble jarvis">
                <span className="who">Jarvis</span>
                {t.steps.length > 0 && (
                  <div className="substeps">
                    {t.steps.map((s, i) => (
                      <div className="substep" key={i}><span className="kind">{s.kind}</span><span className="lbl">{s.label}{s.detail ? <span className="det"> — {s.detail}</span> : null}</span></div>
                    ))}
                    {t.loading && <LiveProgress startedAt={t.startedAt} steps={t.steps.length} />}
                  </div>
                )}
                {t.loading && t.steps.length === 0 && <div className="w-empty" style={{ padding: 12 }}><span className="spin-fast">◠</span><div className="s">Jarvis is thinking…</div><LiveProgress startedAt={t.startedAt} steps={0} /></div>}
                {t.resp && (isHtmlish(t.resp.answer) || looksLikeRawTool(t.resp.answer)
                  ? <ErrorCard message={looksLikeRawTool(t.resp.answer) ? "I had trouble using a tool for that. Try rephrasing, or switch the model in Settings." : t.resp.answer} />
                  : <><div className="answer-txt">{t.resp.answer}</div>
                      {cacheAgeSeconds(t.resp.model) !== null && (
                        <div className="cache-flag"><span className="ast">*</span> cached answer · ~{fmtAge(cacheAgeSeconds(t.resp.model)!)} old — not a live response</div>
                      )}
                      <div className="answer-meta"><span>{t.resp.agent}</span><span>{cacheAgeSeconds(t.resp.model) !== null ? 'cached' : `model ${t.resp.model}`}</span><span>{t.resp.tokens} tokens</span></div></>)}
                {t.commandResult && (t.commandResult.status === 'ERROR'
                  ? <ErrorCard message={t.commandResult.message} />
                  : <>
                      {t.commandResult.message && <div className="answer-txt">{isHtmlish(t.commandResult.message) ? stripHtml(t.commandResult.message) : t.commandResult.message}</div>}
                      {t.commandResult.data !== undefined && t.commandResult.data !== null && t.commandResult.data !== '' ? <DataView value={t.commandResult.data} /> : null}
                    </>)}
              </div>
            </div>))}
        <div ref={endRef} />
      </div>
    </div>
  )
}
