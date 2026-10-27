import { useState } from 'react'
import type { FormEvent } from 'react'
import { checkLoginId, signup } from '../../api/client.ts'
import { emailError, loginIdError, nicknameError, PASSWORD_HINT, passwordError } from '../../lib/authRules.ts'
import FormField from './FormField.tsx'
import PasswordInput from './PasswordInput.tsx'

type Availability = { id: string; state: 'checking' | 'available' | 'taken' | 'error' } | null

type Props = { onBack: () => void; onDone: (email: string) => void }

export default function SignupForm({ onBack, onDone }: Props) {
  const [loginId, setLoginId] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [nickname, setNickname] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [availability, setAvailability] = useState<Availability>(null)
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const touch = (name: string) => setTouched((current) => ({ ...current, [name]: true }))

  const idFormatError = loginId ? loginIdError(loginId) : null
  const errors = {
    loginId: touched.loginId ? (idFormatError ?? (loginId ? null : '아이디를 입력해 주세요.')) : null,
    email: touched.email ? emailError(email) : null,
    password: touched.password ? passwordError(password, loginId) : null,
    confirm: touched.confirm && confirm !== password ? '비밀번호가 서로 달라요.' : null,
    nickname: touched.nickname ? nicknameError(nickname) : null,
  }
  const normalizedId = loginId.toLowerCase()
  const checked = availability && availability.id === normalizedId ? availability.state : null

  // 아이디 입력창을 벗어나면 형식이 맞을 때 중복을 확인한다.
  const verifyLoginId = async () => {
    touch('loginId')
    if (!loginId || loginIdError(loginId)) {
      setAvailability(null)
      return
    }
    setAvailability({ id: normalizedId, state: 'checking' })
    try {
      const { available } = await checkLoginId(normalizedId)
      setAvailability({ id: normalizedId, state: available ? 'available' : 'taken' })
    } catch {
      setAvailability({ id: normalizedId, state: 'error' })
    }
  }

  const idHint =
    checked === 'checking'
      ? '확인하는 중…'
      : checked === 'available'
        ? '사용할 수 있는 아이디예요.'
        : '(영문으로 시작하는 영문·숫자 5~20자, 대소문자 구분 없음)'
  const idError = errors.loginId ?? (checked === 'taken' ? '이미 사용 중인 아이디예요.' : null)

  const clientValid =
    !loginIdError(loginId) &&
    !emailError(email) &&
    !passwordError(password, loginId) &&
    confirm === password &&
    !nicknameError(nickname) &&
    agreed &&
    checked !== 'taken'

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    setTouched({ loginId: true, email: true, password: true, confirm: true, nickname: true })
    if (!clientValid) return
    setBusy(true)
    setSubmitError(null)
    try {
      await signup({ loginId: normalizedId, email: email.trim(), password, nickname: nickname.trim(), agreedToTerms: agreed })
      onDone(email.trim())
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : '가입 요청에 실패했어요.')
      setBusy(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={submit} noValidate>
      <FormField label="아이디" hint={idHint} error={idError}>
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
            onBlur={verifyLoginId}
          />
        )}
      </FormField>
      <FormField label="이메일" hint="(인증과 아이디·비밀번호 찾기에 쓰여요)" error={errors.email}>
        {({ id, describedBy, invalid }) => (
          <input
            id={id}
            type="email"
            value={email}
            autoComplete="email"
            aria-describedby={describedBy}
            aria-invalid={invalid || undefined}
            onChange={(event) => setEmail(event.target.value)}
            onBlur={() => touch('email')}
          />
        )}
      </FormField>
      <FormField label="비밀번호" hint={PASSWORD_HINT} error={errors.password}>
        {({ id, describedBy, invalid }) => (
          <PasswordInput
            id={id}
            value={password}
            onChange={(value) => {
              setPassword(value)
              touch('password')
            }}
            autoComplete="new-password"
            describedBy={describedBy}
            invalid={invalid}
          />
        )}
      </FormField>
      <FormField label="비밀번호 확인" error={errors.confirm}>
        {({ id, describedBy, invalid }) => (
          <PasswordInput
            id={id}
            value={confirm}
            onChange={(value) => {
              setConfirm(value)
              touch('confirm')
            }}
            autoComplete="new-password"
            describedBy={describedBy}
            invalid={invalid}
          />
        )}
      </FormField>
      <FormField label="닉네임" hint="(다른 사람에게 보이는 이름, 2~20자)" error={errors.nickname}>
        {({ id, describedBy, invalid }) => (
          <input
            id={id}
            value={nickname}
            autoComplete="nickname"
            aria-describedby={describedBy}
            aria-invalid={invalid || undefined}
            onChange={(event) => setNickname(event.target.value)}
            onBlur={() => touch('nickname')}
          />
        )}
      </FormField>

      <div className="consent">
        <label className="check">
          <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />
          개인정보 수집·이용에 동의해요 (필수)
        </label>
        <details>
          <summary>내용 보기</summary>
          <dl className="consent__table">
            <dt>수집 항목</dt>
            <dd>아이디, 이메일, 비밀번호(암호화해 저장), 닉네임</dd>
            <dt>이용 목적</dt>
            <dd>회원 식별, 로그인, 이메일 인증, 아이디·비밀번호 찾기</dd>
            <dt>보유 기간</dt>
            <dd>회원 탈퇴 시까지</dd>
          </dl>
        </details>
      </div>

      {submitError && (
        <p className="notice notice--error" role="alert">
          {submitError}
        </p>
      )}
      <button type="submit" className="btn btn--on btn--block" disabled={busy}>
        {busy ? '인증 메일 보내는 중…' : '인증 메일 받기'}
      </button>
      <div className="auth-form__links">
        <button type="button" className="link-btn" onClick={onBack}>
          ← 로그인으로
        </button>
      </div>
    </form>
  )
}
