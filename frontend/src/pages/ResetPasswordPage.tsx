import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { resetPassword } from '../api/client.ts'
import AuthPageLayout from '../components/AuthPageLayout.tsx'
import FormField from '../components/auth/FormField.tsx'
import PasswordChecklist from '../components/auth/PasswordChecklist.tsx'
import PasswordInput from '../components/auth/PasswordInput.tsx'
import { passwordError } from '../lib/authRules.ts'

/** 비밀번호 재설정 메일의 링크(/reset-password?token=...)가 열리는 화면. */
export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)

  if (!token) {
    return (
      <AuthPageLayout title="비밀번호 재설정">
        <p className="notice notice--error" role="alert">
          재설정 링크가 올바르지 않아요. 메일의 링크를 그대로 열어 주세요.
        </p>
        <Link className="btn btn--block" to="/">
          지도로 돌아가기
        </Link>
      </AuthPageLayout>
    )
  }

  if (done) {
    return (
      <AuthPageLayout title="비밀번호 재설정">
        <p className="notice" role="status">
          비밀번호를 바꿨어요. 보안을 위해 다른 기기에서는 모두 로그아웃돼요.
        </p>
        <Link className="btn btn--on btn--block" to="/?login=1">
          로그인하러 가기
        </Link>
      </AuthPageLayout>
    )
  }

  // 서버가 규칙을 다시 검사하며, 아이디 포함 여부는 서버만 알 수 있다.
  const clientError = passwordError(password, '')
  const mismatch = confirm !== password ? '비밀번호가 서로 달라요.' : null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    setTouched(true)
    if (clientError || mismatch) return
    setBusy(true)
    setError(null)
    try {
      await resetPassword(token, password)
      setDone(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : '비밀번호를 바꾸지 못했어요.')
      setBusy(false)
    }
  }

  return (
    <AuthPageLayout title="새 비밀번호 정하기">
      <form className="auth-form" onSubmit={submit} noValidate>
        <FormField label="새 비밀번호" error={touched ? clientError : null}>
          {({ id, describedBy, invalid }) => (
            <PasswordInput
              id={id}
              value={password}
              onChange={(value) => {
                setPassword(value)
                setTouched(true)
              }}
              autoComplete="new-password"
              describedBy={describedBy}
              invalid={invalid}
            />
          )}
        </FormField>
        <PasswordChecklist password={password} />
        <FormField label="새 비밀번호 확인" error={touched ? mismatch : null}>
          {({ id, describedBy, invalid }) => (
            <PasswordInput
              id={id}
              value={confirm}
              onChange={setConfirm}
              autoComplete="new-password"
              describedBy={describedBy}
              invalid={invalid}
            />
          )}
        </FormField>
        {error && (
          <p className="notice notice--error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="btn btn--on btn--block" disabled={busy}>
          {busy ? '바꾸는 중…' : '비밀번호 바꾸기'}
        </button>
      </form>
    </AuthPageLayout>
  )
}
