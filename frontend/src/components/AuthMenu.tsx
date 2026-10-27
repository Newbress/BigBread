import { useRef } from 'react'
import type { AuthOptions, AuthProvider, User } from '../api/types.ts'
import { isHttpUrl } from '../lib/format.ts'
import { wantsLoginOnLoad } from '../lib/loginParam.ts'
import LoginDialog from './auth/LoginDialog.tsx'

type Props = {
  loading: boolean
  providers: AuthProvider[]
  options: AuthOptions
  user: User | null
  error: string | null
  onLogin: (loginId: string, password: string) => Promise<void>
  onLogout: () => void
  /** 로그인 창이 열려 있는지 (다른 화면에서도 열 수 있도록 부모가 가진다) */
  loginOpen: boolean
  onLoginOpenChange: (open: boolean) => void
}

export default function AuthMenu({
  loading,
  providers,
  options,
  user,
  error,
  onLogin,
  onLogout,
  loginOpen: open,
  onLoginOpenChange,
}: Props) {
  const triggerRef = useRef<HTMLButtonElement>(null)

  const close = () => {
    onLoginOpenChange(false)
    // 창을 닫으면 로그인 버튼으로 포커스를 돌려준다.
    queueMicrotask(() => triggerRef.current?.focus())
    if (wantsLoginOnLoad()) window.history.replaceState(null, '', window.location.pathname)
  }

  if (loading) return null

  if (user) {
    return (
      <div className="auth">
        {isHttpUrl(user.profileImageUrl) && (
          <img className="auth__avatar" src={user.profileImageUrl} alt="" referrerPolicy="no-referrer" />
        )}
        <span className="auth__name">{user.nickname}</span>
        <button type="button" className="btn btn--small" onClick={onLogout}>
          로그아웃
        </button>
        {error && (
          <p className="notice notice--error auth__error" role="alert">
            {error}
          </p>
        )}
      </div>
    )
  }

  // 소셜 제공자도 없고 가입도 막혀 있으면 로그인 기능 자체를 숨긴다.
  if (providers.length === 0 && !options.signupEnabled) return null

  return (
    <div className="auth">
      <button ref={triggerRef} type="button" className="btn btn--small" onClick={() => onLoginOpenChange(true)}>
        로그인
      </button>
      {open && (
        <LoginDialog
          providers={providers}
          signupEnabled={options.signupEnabled}
          onLogin={async (loginId, password) => {
            await onLogin(loginId, password)
            close()
          }}
          onClose={close}
        />
      )}
    </div>
  )
}
