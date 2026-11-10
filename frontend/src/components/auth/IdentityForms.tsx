import { useState } from 'react'
import type { FormEvent } from 'react'
import { findLoginIdByIdentity, resetPasswordByIdentity } from '../../api/client.ts'
import { PASSWORD_HINT, passwordError } from '../../lib/authRules.ts'
import FormField from './FormField.tsx'
import IdentityStep from './IdentityStep.tsx'
import PasswordInput from './PasswordInput.tsx'

type Props = { provider: string; onBack: () => void }

/** 아이디 찾기: 휴대폰 본인 인증을 마치면 그 사람의 아이디를 알려 준다. */
export function IdentityFindIdForm({ provider, onBack }: Props) {
  const [verifying, setVerifying] = useState(false)
  const [found, setFound] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (verifying) {
    return (
      <IdentityStep
        provider={provider}
        purpose="FIND_ID"
        onCancel={() => setVerifying(false)}
        onTicket={(ticket) => {
          setVerifying(false)
          findLoginIdByIdentity(ticket)
            .then((result) => setFound(result.loginId))
            .catch((e: unknown) => setError(e instanceof Error ? e.message : '아이디를 찾지 못했어요.'))
        }}
      />
    )
  }
  if (found) {
    return (
      <div className="auth-form">
        <p className="notice" role="status">
          가입하신 아이디는 <strong>{found}</strong> 예요.
        </p>
        <button type="button" className="btn btn--block" onClick={onBack}>
          로그인으로
        </button>
      </div>
    )
  }
  return (
    <div className="auth-form">
      <p>휴대폰 본인 인증으로 가입하신 아이디를 찾아 드려요.</p>
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}
      <button type="button" className="btn btn--on btn--block" onClick={() => setVerifying(true)}>
        본인 인증하고 아이디 찾기
      </button>
      <div className="auth-form__links">
        <button type="button" className="link-btn" onClick={onBack}>
          ← 로그인으로
        </button>
      </div>
    </div>
  )
}

/** 비밀번호 찾기: 아이디와 새 비밀번호를 정하고, 본인 인증이 가입한 사람과 같을 때만 바뀐다. */
export function IdentityResetForm({ provider, onBack }: Props) {
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [touched, setTouched] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  const normalized = loginId.trim().toLowerCase()
  const passwordProblem = touched ? passwordError(password, normalized) : null
  const confirmProblem = touched && confirm !== password ? '비밀번호가 서로 달라요.' : null
  const valid = normalized !== '' && !passwordError(password, normalized) && confirm === password

  const start = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    setError(null)
    if (valid) setVerifying(true)
  }

  if (done) {
    return (
      <div className="auth-form">
        <p className="notice" role="status">
          비밀번호를 바꿨어요. 다른 기기의 로그인은 모두 풀렸어요. 새 비밀번호로 로그인해 주세요.
        </p>
        <button type="button" className="btn btn--block" onClick={onBack}>
          로그인으로
        </button>
      </div>
    )
  }
  if (verifying) {
    return (
      <IdentityStep
        provider={provider}
        purpose="RESET"
        onCancel={() => setVerifying(false)}
        onTicket={(ticket) => {
          setVerifying(false)
          resetPasswordByIdentity(ticket, normalized, password)
            .then(() => setDone(true))
            .catch((e: unknown) => setError(e instanceof Error ? e.message : '비밀번호를 바꾸지 못했어요.'))
        }}
      />
    )
  }
  return (
    <form className="auth-form" onSubmit={start} noValidate>
      <FormField label="아이디">
        {({ id, describedBy, invalid }) => (
          <input
            id={id}
            value={loginId}
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            aria-describedby={describedBy}
            aria-invalid={invalid || undefined}
            onChange={(event) => setLoginId(event.target.value)}
          />
        )}
      </FormField>
      <FormField label="새 비밀번호" hint={PASSWORD_HINT} error={passwordProblem}>
        {({ id, describedBy, invalid }) => (
          <PasswordInput id={id} value={password} onChange={setPassword} autoComplete="new-password" describedBy={describedBy} invalid={invalid} />
        )}
      </FormField>
      <FormField label="새 비밀번호 확인" error={confirmProblem}>
        {({ id, describedBy, invalid }) => (
          <PasswordInput id={id} value={confirm} onChange={setConfirm} autoComplete="new-password" describedBy={describedBy} invalid={invalid} />
        )}
      </FormField>
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}
      <button type="submit" className="btn btn--on btn--block">
        본인 인증하고 비밀번호 바꾸기
      </button>
      <div className="auth-form__links">
        <button type="button" className="link-btn" onClick={onBack}>
          ← 로그인으로
        </button>
      </div>
    </form>
  )
}
