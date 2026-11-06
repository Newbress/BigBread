import { useEffect, useRef, useState } from 'react'
import { changePassword, fetchAccountInfo, withdrawAccount } from '../../api/client.ts'
import type { AccountInfo, User } from '../../api/types.ts'
import { PASSWORD_HINT, passwordError } from '../../lib/authRules.ts'
import { ConfirmButton } from '../admin/common.tsx'
import FormField from './FormField.tsx'
import PasswordInput from './PasswordInput.tsx'

type Props = {
  user: User
  onClose: () => void
  /** 탈퇴가 끝나고 안내를 닫았을 때 */
  onDeleted: () => void
}

const PROVIDER_LABEL: Record<string, string> = { KAKAO: '카카오', NAVER: '네이버', GOOGLE: '구글' }

function PasswordSection({ info }: { info: AccountInfo }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [touched, setTouched] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  const newError = touched ? passwordError(next, info.loginId ?? '') : null
  const confirmError = touched && confirm !== next ? '비밀번호가 서로 달라요.' : null

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setTouched(true)
    setDone(false)
    setError(null)
    if (!current || passwordError(next, info.loginId ?? '') || confirm !== next) return
    setBusy(true)
    try {
      await changePassword(current, next)
      setDone(true)
      setCurrent('')
      setNext('')
      setConfirm('')
      setTouched(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : '비밀번호를 바꾸지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="account-section" onSubmit={submit} aria-labelledby="account-pw-title">
      <h3 id="account-pw-title">비밀번호 변경</h3>
      <FormField label="현재 비밀번호">
        {({ id, describedBy }) => <PasswordInput id={id} value={current} onChange={setCurrent} autoComplete="current-password" describedBy={describedBy} />}
      </FormField>
      <FormField label="새 비밀번호" hint={PASSWORD_HINT} error={newError}>
        {({ id, describedBy, invalid }) => (
          <PasswordInput
            id={id}
            value={next}
            onChange={(v) => {
              setNext(v)
              setTouched(true)
            }}
            autoComplete="new-password"
            describedBy={describedBy}
            invalid={invalid}
          />
        )}
      </FormField>
      <FormField label="새 비밀번호 확인" error={confirmError}>
        {({ id, describedBy, invalid }) => (
          <PasswordInput id={id} value={confirm} onChange={setConfirm} autoComplete="new-password" describedBy={describedBy} invalid={invalid} />
        )}
      </FormField>
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}
      {done && (
        <p className="notice" role="status">
          비밀번호를 바꿨어요. 다른 기기의 로그인은 모두 해제됐고, 확인 메일을 보냈어요.
        </p>
      )}
      <button type="submit" className="btn btn--primary" disabled={busy}>
        {busy ? '바꾸는 중…' : '비밀번호 바꾸기'}
      </button>
    </form>
  )
}

function WithdrawSection({ info, onDone }: { info: AccountInfo; onDone: () => void }) {
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const ready = info.hasPassword ? password.length > 0 : confirm.trim() === '탈퇴'

  async function run() {
    setBusy(true)
    setError(null)
    try {
      await withdrawAccount(info.hasPassword ? { password } : { confirm: confirm.trim() })
      onDone()
    } catch (e) {
      setError(e instanceof Error ? e.message : '탈퇴하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="account-section account-section--danger" aria-labelledby="account-del-title">
      <h3 id="account-del-title">회원 탈퇴</h3>
      <p>탈퇴하면 아래 정보가 바로 지워지고 되돌릴 수 없어요.</p>
      <ul>
        <li>계정 정보(닉네임, 이메일, 로그인 정보)</li>
        <li>내가 쓴 리뷰와 올린 사진</li>
        <li>즐겨찾기, 저장한 코스, 보낸 제보와 신고</li>
      </ul>
      {info.hasPassword ? (
        <FormField label="비밀번호를 입력해 본인 확인">
          {({ id, describedBy }) => <PasswordInput id={id} value={password} onChange={setPassword} autoComplete="current-password" describedBy={describedBy} />}
        </FormField>
      ) : (
        <FormField label="확인을 위해 '탈퇴'를 입력해 주세요">
          {({ id }) => <input id={id} value={confirm} autoComplete="off" onChange={(e) => setConfirm(e.target.value)} />}
        </FormField>
      )}
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}
      <ConfirmButton label="탈퇴하기" confirmLabel="정말 탈퇴" disabled={!ready || busy} onConfirm={() => void run()} />
    </section>
  )
}

/** 내 계정 창: 로그인 방법 확인, 비밀번호 변경(자체 가입 계정), 회원 탈퇴. */
export default function AccountDialog({ user, onClose, onDeleted }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const [info, setInfo] = useState<AccountInfo | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [deleted, setDeleted] = useState(false)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    fetchAccountInfo(controller.signal)
      .then(setInfo)
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setLoadError(e instanceof Error ? e.message : '계정 정보를 불러오지 못했어요.')
      })
    return () => controller.abort()
  }, [])

  return (
    <dialog
      ref={ref}
      className="login-dialog report-dialog"
      aria-labelledby="account-title"
      onClose={deleted ? onDeleted : onClose}
    >
      <div className="report-dialog__body">
        <button type="button" className="login-dialog__close" aria-label="닫기" onClick={() => ref.current?.close()}>
          ×
        </button>
        <h2 id="account-title">내 계정</h2>
        {deleted ? (
          <>
            <p className="notice" role="status">
              탈퇴가 끝났어요. 그동안 이용해 주셔서 감사합니다.
            </p>
            <button type="button" className="btn btn--primary" onClick={() => ref.current?.close()}>
              확인
            </button>
          </>
        ) : (
          <>
            <p>
              <strong>{user.nickname}</strong>
              {info && (
                <span className="card__meta">
                  {' '}
                  ·{' '}
                  {info.hasPassword
                    ? `아이디 ${info.loginId}`
                    : `${info.providers.map((p) => PROVIDER_LABEL[p] ?? p).join(', ')} 로그인`}
                </span>
              )}
            </p>
            {loadError && (
              <p className="notice notice--error" role="alert">
                {loadError}
              </p>
            )}
            {info && info.hasPassword && <PasswordSection info={info} />}
            {info && !info.hasPassword && (
              <p className="card__meta">소셜 로그인 계정은 비밀번호가 없어요. 로그인 정보는 해당 서비스에서 관리해 주세요.</p>
            )}
            {info && <WithdrawSection info={info} onDone={() => setDeleted(true)} />}
          </>
        )}
      </div>
    </dialog>
  )
}
