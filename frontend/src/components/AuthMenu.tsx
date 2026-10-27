import { useRef, useState } from 'react'
import type { AuthOptions, AuthProvider, User } from '../api/types.ts'
import { isHttpUrl } from '../lib/format.ts'
import LoginDialog from './auth/LoginDialog.tsx'

type Props = {
  loading: boolean
  providers: AuthProvider[]
  options: AuthOptions
  user: User | null
  error: string | null
  onLogin: (loginId: string, password: string) => Promise<void>
  onLogout: () => void
}

/** 메일 링크(가입 완료 등)에서 돌아올 때 로그인 창을 바로 열기 위한 주소 파라미터(?login=1). */
function wantsLoginOnLoad(): boolean {
  return new URLSearchParams(window.location.search).get('login') === '1'
}

export default function AuthMenu({ loading, providers, options, user, error, onLogin, onLogout }: Props) {
  const [open, setOpen] = useState(wantsLoginOnLoad)
  const triggerRef = useRef<HTMLButtonElement>(null)

  const close = () => {
    setOpen(false)
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
      <button ref={triggerRef} type="button" className="btn btn--small" onClick={() => setOpen(true)}>
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
