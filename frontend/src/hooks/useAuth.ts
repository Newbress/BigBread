import { useCallback, useEffect, useState } from 'react'
import { fetchMe, fetchProviders, logout as requestLogout } from '../api/client.ts'
import type { AuthProvider, User } from '../api/types.ts'

type State = {
  loading: boolean
  providers: AuthProvider[]
  user: User | null
}

/** 로그인 상태와 로그인 가능한 제공자를 한 번 불러온다. 실패하면 비로그인으로 둔다(지도는 계속 쓸 수 있어야 한다). */
export function useAuth() {
  const [state, setState] = useState<State>({ loading: true, providers: [], user: null })
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([fetchProviders(controller.signal), fetchMe(controller.signal)])
      .then(([providers, me]) => setState({ loading: false, providers, user: me.authenticated ? me.user : null }))
      .catch(() => {
        if (!controller.signal.aborted) setState({ loading: false, providers: [], user: null })
      })
    return () => controller.abort()
  }, [])

  const logout = useCallback(async () => {
    try {
      await requestLogout()
      setState((current) => ({ ...current, user: null }))
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '로그아웃에 실패했어요.')
    }
  }, [])

  return { ...state, error, logout }
}
