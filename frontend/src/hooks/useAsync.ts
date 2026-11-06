import { useCallback, useEffect, useRef, useState } from 'react'

type Result<T> = { token: string; data: T | null; error: string | null }

/**
 * 키가 바뀌거나 reload()를 부르면 다시 불러온다. 새 응답이 오기 전까지는 이전 데이터를 유지해서 화면이 깜빡이지 않는다.
 * 이전 요청은 취소한다.
 */
export function useAsync<T>(load: (signal: AbortSignal) => Promise<T>, key: string) {
  const [version, setVersion] = useState(0)
  const [result, setResult] = useState<Result<T> | null>(null)
  const loadRef = useRef(load)
  useEffect(() => {
    loadRef.current = load
  })

  const token = `${key}#${version}`
  useEffect(() => {
    const controller = new AbortController()
    loadRef
      .current(controller.signal)
      .then((data) => setResult({ token, data, error: null }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setResult((prev) => ({
          token,
          data: prev?.data ?? null,
          error: e instanceof Error ? e.message : '불러오지 못했어요.',
        }))
      })
    return () => controller.abort()
  }, [token])

  const reload = useCallback(() => setVersion((n) => n + 1), [])
  const settled = result !== null && result.token === token
  return { data: result?.data ?? null, loading: !settled, error: settled ? result.error : null, reload }
}
