import { describe, it, expect } from 'vitest'
import { isLongSpeech, firstSentences, shapeSpeech, wantsContinue, speakSmart, takeLongSpeech } from './voice'

const LONG = 'First sentence here. Second sentence here. Third sentence here. Fourth one too.'

describe('voice shaping', () => {
  it('classifies long vs short answers', () => {
    expect(isLongSpeech('Short answer.')).toBe(false)
    expect(isLongSpeech(LONG)).toBe(true)                 // > 2 sentences
    expect(isLongSpeech('x'.repeat(300))).toBe(true)      // > 240 chars
  })

  it('extracts the first N sentences as the spoken summary', () => {
    expect(firstSentences(LONG, 2)).toBe('First sentence here. Second sentence here.')
  })

  it('shapeSpeech: short is read in full, long is summarized + offers the rest', () => {
    expect(shapeSpeech('All good.')).toEqual({ spoken: 'All good.', shortened: false })
    const long = shapeSpeech(LONG)
    expect(long.shortened).toBe(true)
    expect(long.spoken).toContain('continue')
    expect(long.spoken).toContain('First sentence here.')
  })

  it('recognizes "continue" follow-ups but not normal questions', () => {
    for (const t of ['continue', 'keep going', 'tell me more', 'go on', 'read the rest', 'elaborate']) {
      expect(wantsContinue(t)).toBe(true)
    }
    expect(wantsContinue('what is recursion')).toBe(false)
    expect(wantsContinue('continental breakfast options')).toBe(false)   // word-boundary, not prefix
  })

  it('speakSmart stashes the full text of a long answer for one "continue", then clears it', () => {
    speakSmart(LONG)                       // speechSynthesis is unavailable in jsdom — speak() no-ops safely
    expect(takeLongSpeech()).toBe(LONG)    // consumable once
    expect(takeLongSpeech()).toBeNull()    // already taken
    speakSmart('Short answer.')            // short answers stash nothing
    expect(takeLongSpeech()).toBeNull()
  })
})
