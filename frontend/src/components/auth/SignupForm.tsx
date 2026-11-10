import { useState } from 'react'
import type { FormEvent } from 'react'
import { checkLoginId, signup } from '../../api/client.ts'
import { emailError, loginIdError, nicknameError, PASSWORD_HINT, passwordError } from '../../lib/authRules.ts'
import FormField from './FormField.tsx'
import IdentityStep from './IdentityStep.tsx'
import PasswordInput from './PasswordInput.tsx'

type Availability = { id: string; state: 'checking' | 'available' | 'taken' | 'error' } | null

type Props = {
  /** 이메일 칸을 보여줄지 (false 면 이메일 없이 가입) */
  collectEmail: boolean
  /** true 면 인증 메일을 보내고, false 면 바로 가입하고 로그인한다 */
  verifyEmail: boolean
  /** 휴대폰 본인확인(PASS)으로 가입할 때 제공자 이름. null 이면 본인확인을 쓰지 않는다 */
  identityProvider: string | null
  onLogin: (loginId: string, password: string) => Promise<void>
  onBack: () => void
  onDone: (email: string) => void
}

export default function SignupForm({ collectEmail, verifyEmail, identityProvider, onLogin, onBack, onDone }: Props) {
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
  // 본인확인 가입: 인증을 마쳐 확인표를 받은 뒤에야 입력 폼이 나온다
  const [ticket, setTicket] = useState<string | null>(null)

  const touch = (name: string) => setTouched((current) => ({ ...current, [name]: true }))

  const idFormatError = loginId ? loginIdError(loginId) : null
  const errors = {
    loginId: touched.loginId ? (idFormatError ?? (loginId ? null : '아이디를 입력해 주세요.')) : null,
    email: collectEmail && touched.email ? emailError(email) : null,
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
    (!collectEmail || !emailError(email)) &&
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
      await signup({
        loginId: normalizedId,
        email: collectEmail ? email.trim() : '',
        password,
        nickname: nickname.trim(),
        agreedToTerms: agreed,
        ...(ticket ? { identityTicket: ticket } : {}),
      })
      if (verifyEmail) {
        onDone(email.trim())
      } else {
        await onLogin(normalizedId, password) // 가입 직후 바로 로그인한다 (창은 로그인 성공 시 닫힌다)
      }
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : '가입 요청에 실패했어요.')
      setBusy(false)
    }
  }

  if (identityProvider && !ticket) {
    return (
      <div className="auth-form">
        <p>가입하려면 먼저 휴대폰 본인 인증(PASS)이 필요해요. 한 사람이 한 계정만 만들 수 있고, 만 14세 이상만 가입할 수 있어요.</p>
        <IdentityStep provider={identityProvider} purpose="SIGNUP" onTicket={setTicket} onCancel={onBack} />
      </div>
    )
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
      {collectEmail && (
      <FormField
        label="이메일"
        hint={verifyEmail ? '(인증과 아이디·비밀번호 찾기에 쓰여요)' : '(아이디·비밀번호를 잊었을 때 찾는 데만 쓰여요. 정확히 입력해 주세요)'}
 error={errors.email}
      >
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
      )}
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
          <span>
            <a href="/terms" target="_blank" rel="noreferrer">이용약관</a>과{' '}
            <a href="/privacy" target="_blank" rel="noreferrer">개인정보 처리방침</a>에 동의해요 (필수)
          </span>
        </label>
        <details>
          <summary>내용 보기</summary>
          <dl className="consent__table">
            <dt>수집 항목</dt>
            <dd>
              {collectEmail
                ? '아이디, 이메일, 비밀번호(암호화해 저장), 닉네임'
                : identityProvider
                  ? '아이디, 비밀번호(암호화해 저장), 닉네임, 본인 인증 정보(복원할 수 없는 해시값만 저장, 이름·생년월일·전화번호는 저장 안 함)'
                  : '아이디, 비밀번호(암호화해 저장), 닉네임'}
            </dd>
            <dt>이용 목적</dt>
            <dd>
              {collectEmail
                ? verifyEmail
                  ? '회원 식별, 로그인, 이메일 인증, 아이디·비밀번호 찾기'
                  : '회원 식별, 로그인, 아이디·비밀번호 찾기'
                : identityProvider
                  ? '회원 식별, 로그인, 중복 가입 방지, 본인 인증을 통한 아이디·비밀번호 찾기'
                  : '회원 식별, 로그인'}
            </dd>
            <dt>보유 기간</dt>
            <dd>회원 탈퇴 시까지</dd>
          </dl>
        </details>
      </div>

      {!collectEmail && (
        <p className="login-dialog__note">
          {identityProvider
            ? '본인 인증은 가입이 끝나면 더 이상 필요하지 않아요. 비밀번호를 잊으면 같은 본인 인증으로 다시 정할 수 있어요.'
            : '이메일을 받지 않는 대신 비밀번호를 잊으면 스스로 찾을 수 없어요. 비밀번호는 꼭 기억해 두세요. 잊었다면 운영자에게 문의하면 임시 비밀번호를 받을 수 있어요.'}
        </p>
      )}

      {submitError && (
        <p className="notice notice--error" role="alert">
          {submitError}
        </p>
      )}
      <button type="submit" className="btn btn--on btn--block" disabled={busy}>
        {verifyEmail ? (busy ? '인증 메일 보내는 중…' : '인증 메일 받기') : busy ? '가입하는 중…' : '가입하기'}
      </button>
      <div className="auth-form__links">
        <button type="button" className="link-btn" onClick={onBack}>
          ← 로그인으로
        </button>
      </div>
    </form>
  )
}
