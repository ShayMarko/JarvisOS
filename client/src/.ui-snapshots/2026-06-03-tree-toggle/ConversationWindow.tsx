import { useEffect, useRef, useState } from 'react'
import type { Step } from '../../api'
import type { Turn } from '../../types'
import { cacheAgeSeconds, fmtAge, isHtmlish, looksLikeRawTool, stripHtml } from '../../lib/format'
import { DataView } from '../DataView'
import { ErrorCard } from '../ErrorCard'
import { LiveProgress } from '../LiveProgress'

type StepView = 'list' | 'tree'
const VIEW_KEY = 'jarvis.convoStepView'

const KIND_GLYPH: Record<string, string> = {
  root: '◈', intent: '◍', agent: '⬡', model: '◆', tool: '▸', answer: '✓', error: '✕',
}

/** The current line-under-line trace — unchanged. */
function StepList({ steps }: { steps: Step[] }) {
  return (
    <div className="substeps">
      {steps.map((s, i) => (
        <div className="substep" key={i}>
          <span className="kind">{s.kind}</span>
          <span className="lbl">{s.label}{s.detail ? <span className="det"> — {s.detail}</span> : null}</span>
        </div>
      ))}
    </div>
  )
}

type FlowNode = { kind: string; label: string; detail?: string; children: FlowNode[] }

/**
 * Turn the flat step list into a flow TREE: the request is the root; pre-processing (intent) and the
 * final answer are top-level branches; each agent is a branch whose model + tool calls are its children.
 * Multiple agents (a Planner run) each become their own branch with their own tool children.
 */
function buildFlowTree(steps: Step[], prompt: string): FlowNode {
  const root: FlowNode = { kind: 'root', label: prompt.trim() || 'Request', children: [] }
  let agent: FlowNode | null = null
  for (const s of steps) {
    const node: FlowNode = { kind: s.kind, label: s.label, detail: s.detail ?? undefined, children: [] }
    if (s.kind === 'agent') {
      agent = node
      root.children.push(node)
    } else if ((s.kind === 'model' || s.kind === 'tool') && agent) {
      agent.children.push(node)        // these belong to the agent that just ran
    } else {
      root.children.push(node)         // intent / answer / error → top-level branches
    }
  }
  return root
}

function TreeNode({ node }: { node: FlowNode }) {
  return (
    <li>
      <div className={`tn k-${node.kind}`}>
        <span className="tn-head"><span className="tn-glyph">{KIND_GLYPH[node.kind] ?? '•'}</span>
          <span className="tn-kind">{node.kind === 'root' ? 'prompt' : node.kind}</span></span>
        <span className="tn-label">{node.label}</span>
        {node.detail ? <span className="tn-det">{node.detail}</span> : null}
      </div>
      {node.children.length > 0 && (
        <ul>{node.children.map((c, i) => <TreeNode key={i} node={c} />)}</ul>
      )}
    </li>
  )
}

/** The same trace as a top-down org-chart diagram: boxes connected by downward branch lines. */
function StepTree({ steps, prompt }: { steps: Step[]; prompt: string }) {
  return <div className="treeview"><ul><TreeNode node={buildFlowTree(steps, prompt)} /></ul></div>
}

/** The running chat transcript: one reused window, every exchange appended. */
export function ConversationWindow({ turns, onClear }: { turns: Turn[]; onClear: () => void }) {
  const endRef = useRef<HTMLDivElement | null>(null)
  const [view, setView] = useState<StepView>(() =>
    (localStorage.getItem(VIEW_KEY) as StepView) === 'tree' ? 'tree' : 'list')
  useEffect(() => { localStorage.setItem(VIEW_KEY, view) }, [view])
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [turns])

  return (
    <div className="convo">
      <div className="convo-bar">
        <span className="grow" style={{ color: 'var(--muted)', fontSize: 12 }}>{turns.length} exchange{turns.length === 1 ? '' : 's'}</span>
        <div className="seg" role="group" aria-label="Trace view">
          <button className={`seg-btn${view === 'list' ? ' on' : ''}`} onClick={() => setView('list')}>Steps</button>
          <button className={`seg-btn${view === 'tree' ? ' on' : ''}`} onClick={() => setView('tree')}>Tree</button>
        </div>
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
                  <div className="substeps-wrap">
                    {view === 'tree' ? <StepTree steps={t.steps} prompt={t.prompt} /> : <StepList steps={t.steps} />}
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
