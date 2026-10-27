import { useCallback, useEffect, useState } from 'react'
import { fetchAuthOptions, fetchMe, fetchProviders, loginLocal, logout as requestLogout } from '../api/client.ts'
import type { AuthOptions, AuthProvider, User } from '../api/types.ts'

type State = {
  loading: boolean
  providers: AuthProvider[]
  options: AuthOptions
  user: User | null
}

const NO_OPTIONS: AuthOptions = { signupEnabled: false }

/** 로그인 상태와 로그인 방법을 한 번 불러온다. 실패하면 비로그인으로 둔다(지도는 계속 쓸 수 있어야 한다). */
export function useAuth() {
  const [state, setState] = useState<State>({ loading: true, providers: [], options: NO_OPTIONS, user: null })
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([fetchProviders(controller.signal), fetchAuthOptions(controller.signal), fetchMe(controller.signal)])
      .then(([providers, options, me]) =>
        setState({ loading: false, providers, options, user: me.authenticated ? me.user : null }),
      )
      .catch(() => {
        if (!controller.signal.aborted) setState({ loading: false, providers: [], options: NO_OPTIONS, user: null })
      })
    return () => controller.abort()
  }, [])

  /** 아이디/비밀번호 로그인. 실패하면 서버 메시지를 담은 오류를 그대로 던져서 폼이 보여준다. */
  const login = useCallback(async (loginId: string, password: string) => {
    const me = await loginLocal(loginId, password)
    setState((current) => ({ ...current, user: me.authenticated ? me.user : null }))
    setError(null)
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

  return { ...state, error, login, logout }
}
