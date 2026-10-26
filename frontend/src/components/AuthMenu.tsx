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

type LoginDialogProps = { providers: AuthProvider[]; open: boolean; onClose: () => void }

/** 로그인 창. 브라우저 기본 <dialog>라서 포커스 가두기, Esc 닫기, 뒷배경 처리가 기본으로 된다. */
function LoginDialog({ providers, open, onClose }: LoginDialogProps) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      className="login-dialog"
      aria-labelledby="login-title"
      onClose={onClose}
      // 뒷배경(dialog 자신)을 누르면 닫는다. 내용 안쪽은 .login-dialog__body가 받는다.
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div className="login-dialog__body">
        <button type="button" className="login-dialog__close" aria-label="닫기" onClick={onClose}>
          ×
        </button>
        <p className="login-dialog__logo" aria-hidden="true">
          🥖
        </p>
        <h2 id="login-title">대빵 로그인</h2>
        <p className="login-dialog__desc">소셜 계정으로 간편하게 로그인해요.</p>
        <div className="login-dialog__buttons">
          {providers.map((provider) => (
            <a key={provider.id} className={BRAND_CLASS[provider.id] ?? 'login-btn'} href={provider.loginUrl}>
              {provider.name}로 시작하기
            </a>
          ))}
        </div>
        <p className="login-dialog__note">
          제공자에서 닉네임과 프로필 사진을 받아 계정을 만들어요. 같은 사람이라도 다른 제공자로 로그인하면 별도
          계정으로 취급돼요.
        </p>
      </div>
    </dialog>
  )
}

export default function AuthMenu({ loading, providers, user, error, onLogout }: Props) {
  const [open, setOpen] = useState(false)

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

  // 키가 설정된 제공자가 없으면 로그인 기능 자체를 숨긴다.
  if (providers.length === 0) return null

  return (
    <div className="auth">
      <button type="button" className="btn btn--small" onClick={() => setOpen(true)}>
        로그인
      </button>
      <LoginDialog providers={providers} open={open} onClose={() => setOpen(false)} />
    </div>
  )
}
