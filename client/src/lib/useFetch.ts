import { type DependencyList, useCallback, useEffect, useState } from 'react'

/**
 * Fetch-on-mount with a manual refresh() — the shared idiom behind almost every HUD window.
 * `data` is null until the first load resolves; on error it falls back to `fallback` (so list windows
 * show an empty state instead of an endless spinner). Pass `deps` when the fetcher closes over props/state.
 */
export function useFetch<T>(fetcher: () => Promise<T>, fallback: T | null = null, deps: DependencyList = []) {
  const [data, setData] = useState<T | null>(null)
  // The fetcher's identity is intentionally controlled via `deps`, not auto-tracked.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const refresh = useCallback(() => { fetcher().then(setData).catch(() => setData(fallback)) }, deps)
  useEffect(() => { refresh() }, [refresh])
  return { data, setData, refresh }
}
