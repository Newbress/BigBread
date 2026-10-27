import { useState } from 'react'
import type { FormEvent } from 'react'
import { findLoginId, forgotPassword } from '../../api/client.ts'
import { emailError } from '../../lib/authRules.ts'
import FormField from './FormField.tsx'

type SingleProps = { onBack: () => void }

/** 입력 하나를 받아 서버에 요청하고, 서버가 준 안내 문구를 그대로 보여주는 폼 (계정이 있든 없든 안내는 같다). */
function SingleFieldForm({
  label,
  hint,
  inputType,
  autoComplete,
  submitLabel,
  validate,
  request,
  onBack,
}: SingleProps & {
  label: string
  hint: string
  inputType: 'text' | 'email'
  autoComplete: string
  submitLabel: string
  validate: (value: string) => string | null
  request: (value: string) => Promise<{ message: string }>
}) {
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    const invalid = validate(value)
    if (invalid) {
      setError(invalid)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const { message } = await request(value.trim())
      setDone(message)
    } catch (e) {
      setError(e instanceof Error ? e.message : '요청에 실패했어요.')
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <div className="auth-form">
        <p className="notice" role="status">
          {done}
        </p>
        <button type="button" className="btn btn--block" onClick={onBack}>
          로그인으로
        </button>
      </div>
    )
  }

  return (
    <form className="auth-form" onSubmit={submit} noValidate>
      <FormField label={label} hint={hint} error={error}>
        {({ id, describedBy, invalid }) => (
          <input
            id={id}
            type={inputType}
            value={value}
            autoComplete={autoComplete}
            autoCapitalize="none"
            spellCheck={false}
            aria-describedby={describedBy}
            aria-invalid={invalid || undefined}
            onChange={(event) => setValue(event.target.value)}
          />
        )}
      </FormField>
      <button type="submit" className="btn btn--on btn--block" disabled={busy}>
        {busy ? '보내는 중…' : submitLabel}
      </button>
      <div className="auth-form__links">
        <button type="button" className="link-btn" onClick={onBack}>
          ← 로그인으로
        </button>
      </div>
    </form>
  )
}

export function FindIdForm({ onBack }: SingleProps) {
  return (
    <SingleFieldForm
      label="가입한 이메일"
      hint="이 이메일로 가입된 계정이 있으면 아이디를 메일로 보내 드려요."
      inputType="email"
      autoComplete="email"
      submitLabel="아이디 메일 받기"
      validate={(value) => emailError(value)}
      request={findLoginId}
      onBack={onBack}
    />
  )
}

export function ForgotPasswordForm({ onBack }: SingleProps) {
  return (
    <SingleFieldForm
      label="아이디"
      hint="가입된 계정이 있으면 등록된 이메일로 재설정 링크를 보내 드려요 (30분 동안 유효)."
      inputType="text"
      autoComplete="username"
      submitLabel="재설정 링크 받기"
      validate={(value) => (value.trim() ? null : '아이디를 입력해 주세요.')}
      request={forgotPassword}
      onBack={onBack}
    />
  )
}
