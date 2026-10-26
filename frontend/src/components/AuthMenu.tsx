import { useEffect, useRef, useState } from 'react'
import type { AuthProvider, User } from '../api/types.ts'
import { isHttpUrl } from '../lib/format.ts'

type Props = {
  loading: boolean
  providers: AuthProvider[]
  user: User | null
  error: string | null
  onLogout: () => void
}

/** 제공자별 버튼 색. 공식 로고 대신 색과 글자로만 구분한다. */
const BRAND_CLASS: Record<string, string> = {
  kakao: 'login-btn login-btn--kakao',
  naver: 'login-btn login-btn--naver',
  google: 'login-btn login-btn--google',
}

export default function AuthMenu({ loading, providers, user, error, onLogout }: Props) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  // 바깥을 누르거나 Esc를 누르면 닫는다.
  useEffect(() => {
    if (!open) return
    const onPointer = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (loading) return null

  if (user) {
    return (
      <div className="auth" ref={rootRef}>
        {isHttpUrl(user.profileImageUrl) && (
          <img className="auth__avatar" src={user.profileImageUrl} alt="" referrerPolicy="no-referrer" />
        )}
        <span className="auth__name">{user.nickname}</span>
        <button type="button" className="btn btn--small" onClick={onLogout}>
          로그아웃
        </button>
        {error && (
          <p className="notice notice--error auth__popover" role="alert">
            {error}
          </p>
        )}
      </div>
    )
  }

  // 키가 설정된 제공자가 없으면 로그인 기능 자체를 숨긴다.
  if (providers.length === 0) return null

  return (
    <div className="auth" ref={rootRef}>
      <button
        type="button"
        className="btn btn--small"
        aria-expanded={open}
        aria-controls="login-options"
        onClick={() => setOpen((current) => !current)}
      >
        로그인
      </button>
      {open && (
        <div id="login-options" className="auth__popover" role="group" aria-label="로그인 방법">
          {providers.map((provider) => (
            <a key={provider.id} className={BRAND_CLASS[provider.id] ?? 'login-btn'} href={provider.loginUrl}>
              {provider.name}로 시작하기
            </a>
          ))}
        </div>
      )}
    </div>
  )
}
