import { useState } from 'react'
import type { FormEvent } from 'react'
import FormField from './FormField.tsx'
import PasswordInput from './PasswordInput.tsx'

type Props = {
  onLogin: (loginId: string, password: string) => Promise<void>
  showRecovery: boolean
  onSignup: () => void
  onFindId: () => void
  onForgot: () => void
}

export default function LoginForm({ onLogin, showRecovery, onSignup, onFindId, onForgot }: Props) {
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    if (!loginId.trim() || !password) {
      setError('아이디와 비밀번호를 입력해 주세요.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await onLogin(loginId.trim(), password)
    } catch (e) {
      setError(e instanceof Error ? e.message : '로그인에 실패했어요.')
      setBusy(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={submit} noValidate>
      <FormField label="아이디">
        {({ id, describedBy }) => (
          <input
            id={id}
            value={loginId}
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            aria-describedby={describedBy}
            onChange={(event) => setLoginId(event.target.value)}
          />
        )}
      </FormField>
      <FormField label="비밀번호">
        {({ id, describedBy }) => (
          <PasswordInput id={id} value={password} onChange={setPassword} autoComplete="current-password" describedBy={describedBy} />
        )}
      </FormField>
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}
      <button type="submit" className="btn btn--on btn--block" disabled={busy}>
        {busy ? '로그인 중…' : '로그인'}
      </button>
      <div className="auth-form__links">
        {showRecovery && (
          <>
            <button type="button" className="link-btn" onClick={onSignup}>
              회원가입
            </button>
            <button type="button" className="link-btn" onClick={onFindId}>
              아이디 찾기
            </button>
            <button type="button" className="link-btn" onClick={onForgot}>
              비밀번호 찾기
            </button>
          </>
        )}
      </div>
    </form>
  )
}
