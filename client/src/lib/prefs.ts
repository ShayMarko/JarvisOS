/** Tiny localStorage-backed preference helpers (voice, language, tts, …). */
export function pref(key: string, fallback: string): string {
  try { return localStorage.getItem(key) ?? fallback } catch { return fallback }
}

export function savePref(key: string, value: string) {
  try { localStorage.setItem(key, value) } catch { /* ignore */ }
}
