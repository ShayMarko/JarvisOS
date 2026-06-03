import { describe, it, expect } from 'vitest'
import {
  cacheAgeSeconds, fmtAge, looksLikeRawTool, isHtmlish, stripHtml, friendlyError,
  fileKind, kb, parseSteps, runState,
} from './format'

describe('cache flagging', () => {
  it('parses the cache age the backend encodes in the model field', () => {
    expect(cacheAgeSeconds('cache:90')).toBe(90)
    expect(cacheAgeSeconds('cache')).toBe(0)
    expect(cacheAgeSeconds('ollama:llama3.2:3b')).toBeNull()   // a real model → not cached
    expect(cacheAgeSeconds(undefined)).toBeNull()
  })

  it('formats an age for the red flag', () => {
    expect(fmtAge(30)).toBe('30s')
    expect(fmtAge(90)).toBe('2 min')
    expect(fmtAge(3600)).toBe('1 hr')
  })
})

describe('text safety helpers', () => {
  it('detects a leaked raw tool-call/bean dump', () => {
    expect(looksLikeRawTool('{"name":"web_search","parameters":{"query":"x"}}')).toBe(true)
    expect(looksLikeRawTool('Here is your answer.')).toBe(false)
  })

  it('detects + strips HTML', () => {
    expect(isHtmlish('<html><body><p>x</p></body></html>')).toBe(true)
    expect(isHtmlish('just plain text')).toBe(false)
    expect(stripHtml('<b>hi</b> <i>there</i>')).toBe('hi there')
  })

  it('turns raw errors into friendly messages', () => {
    expect(friendlyError(undefined)).toMatch(/something went wrong/i)
    expect(friendlyError('Error: connection refused')).toMatch(/reach that service/i)
    expect(friendlyError('Error: request timed out')).toMatch(/timed out/i)
    expect(friendlyError('permission denied')).toBe('permission denied')   // already user-facing
  })
})

describe('classification + parsing helpers', () => {
  it('maps file extensions to a viewer kind', () => {
    expect(fileKind('photo.png')).toBe('image')
    expect(fileKind('clip.mp4')).toBe('video')
    expect(fileKind('doc.pdf')).toBe('pdf')
    expect(fileKind('notes.md')).toBe('text')
    expect(fileKind('Makefile')).toBe('text')        // no extension → try as text
    expect(fileKind('blob.bin')).toBe('binary')
  })

  it('formats byte sizes', () => {
    expect(kb(500)).toBe('500 B')
    expect(kb(2048)).toBe('2 KB')
    expect(kb(1_572_864)).toBe('1.5 MB')
  })

  it('parses step JSON defensively', () => {
    expect(parseSteps(null)).toEqual([])
    expect(parseSteps('not json')).toEqual([])
    expect(parseSteps('[{"kind":"tool","label":"x"}]')).toHaveLength(1)
  })

  it('maps run status to a visual state', () => {
    expect(runState('OK')).toBe('success')
    expect(runState('FAILED')).toBe('failed')
    expect(runState('RUNNING')).toBe('running')
  })
})
