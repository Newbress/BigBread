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

/** 제공자별 버튼 색. */
const BRAND_CLASS: Record<string, string> = {
  kakao: 'login-btn login-btn--kakao',
  naver: 'login-btn login-btn--naver',
  google: 'login-btn login-btn--google',
}

/**
 * 제공자 심볼. 공식 이미지 파일 대신 단순한 도형으로 직접 그린 것이라 실제 서비스를 공개하기 전에는
 * 각 제공자의 브랜드 가이드(로고 사용 규정)에 맞는 공식 버튼/심볼로 교체하는 것이 안전하다.
 */
function ProviderIcon({ id }: { id: string }) {
  if (id === 'kakao') {
    return (
      <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path
          fill="#191919"
          d="M12 3C6.48 3 2 6.58 2 11c0 2.86 1.88 5.37 4.7 6.78l-1 3.72c-.09.33.28.6.57.4L10.4 19.2c.53.07 1.06.1 1.6.1 5.52 0 10-3.58 10-8S17.52 3 12 3z"
        />
      </svg>
    )
  }
  if (id === 'naver') {
    return (
      <svg viewBox="0 0 20 20" aria-hidden="true" focusable="false">
        <path fill="#fff" d="M13.56 10.7 6.1 0H0v20h6.44V9.3L13.9 20H20V0h-6.44z" />
      </svg>
    )
  }
  if (id === 'google') {
    return (
      <svg viewBox="0 0 18 18" aria-hidden="true" focusable="false">
        <path
          fill="#4285F4"
          d="M17.64 9.2045c0-.6381-.0573-1.2518-.1636-1.8409H9v3.4814h4.8436c-.2086 1.125-.8427 2.0782-1.7959 2.7164v2.2581h2.9087c1.7018-1.5668 2.6836-3.874 2.6836-6.615z"
        />
        <path
          fill="#34A853"
          d="M9 18c2.43 0 4.4673-.806 5.9564-2.1805l-2.9087-2.2581c-.8059.54-1.8368.859-3.0477.859-2.344 0-4.3282-1.5831-5.036-3.7104H.9574v2.3318C2.4382 15.9832 5.4818 18 9 18z"
        />
        <path
          fill="#FBBC05"
          d="M3.964 10.71c-.18-.54-.2822-1.1168-.2822-1.71s.1023-1.17.2823-1.71V4.9582H.9573A8.9965 8.9965 0 0 0 0 9c0 1.4523.3477 2.8268.9573 4.0418L3.964 10.71z"
        />
        <path
          fill="#EA4335"
          d="M9 3.5795c1.3214 0 2.5077.4541 3.4405 1.346l2.5813-2.5814C13.4636.8918 11.4259 0 9 0 5.4818 0 2.4382 2.0168.9573 4.9582L3.964 7.29C4.6718 5.1627 6.6559 3.5795 9 3.5795z"
        />
      </svg>
    )
  }
  return null
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
              <span className="login-btn__icon">
                <ProviderIcon id={provider.id} />
              </span>
              <span className="login-btn__label">{provider.name}로 시작하기</span>
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
