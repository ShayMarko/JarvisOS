/* Voice answer shaping + TTS control. Pure helpers (shapeSpeech / isLongSpeech / firstSentences /
   wantsContinue) are unit-tested; speak/stopSpeaking touch the browser SpeechSynthesis API. */
/* eslint-disable @typescript-eslint/no-explicit-any */

/** The Web Speech recognizer constructor, or null when unsupported. */
export const SR: any = typeof window !== 'undefined'
  ? (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
  : null

export function speak(text: string) {
  try {
    const u = new SpeechSynthesisUtterance(text.slice(0, 600))
    const v = speechSynthesis.getVoices().find((x) => /daniel|arthur|google uk english male/i.test(x.name))
    if (v) u.voice = v
    u.rate = 1.02; u.pitch = 1
    speechSynthesis.cancel(); speechSynthesis.speak(u)
  } catch { /* speech not available */ }
}

/** Stop any in-progress speech immediately (barge-in). */
export function stopSpeaking() { try { speechSynthesis.cancel() } catch { /* n/a */ } }

/** Long enough to warrant a spoken summary instead of reading the whole thing aloud. */
export function isLongSpeech(t: string) { return t.length > 240 || ((t.match(/[.!?]/g)?.length ?? 0) > 2) }

/** First N sentences (or a clipped head) — the 1-2 sentence spoken summary. */
export function firstSentences(t: string, n = 2): string {
  const clean = t.replace(/\s+/g, ' ').trim()
  const parts = clean.match(/[^.!?]+[.!?]+/g)
  if (!parts || parts.length === 0) return clean.slice(0, 240)
  return parts.slice(0, n).map((s) => s.trim()).join(' ')
}

/** Pure decision: what to SAY for an answer, and whether it was shortened (so the rest is offered). */
export function shapeSpeech(full: string): { spoken: string; shortened: boolean } {
  if (isLongSpeech(full)) {
    return { spoken: firstSentences(full, 2) + ' — say "continue" to hear the rest.', shortened: true }
  }
  return { spoken: full, shortened: false }
}

// Full text of the most recent answer whose spoken form was shortened (kept for "continue").
let lastLongSpeech = ''

/** Speak short: long answers get a 1-2 sentence summary + an offer to hear the rest. */
export function speakSmart(full: string) {
  if (!full) return
  const { spoken, shortened } = shapeSpeech(full)
  lastLongSpeech = shortened ? full : ''
  speak(spoken)
}

/** Consume the pending full-length answer (for a "continue" follow-up), clearing it. */
export function takeLongSpeech(): string | null {
  const v = lastLongSpeech
  lastLongSpeech = ''
  return v || null
}

/** "continue / keep going / tell me more / read the rest …" — a voice-control follow-up, not an AI task. */
export const CONTINUE_RE =
  /^\s*(continue|keep going|go on|tell me more|what else|read (the )?(rest|full|more|long)|finish( it)?|more details?|elaborate)\b/i
export function wantsContinue(t: string) { return CONTINUE_RE.test(t) }
