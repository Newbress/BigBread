import { useState } from 'react'
import type { FormEvent } from 'react'
import { verifyIdentity } from '../../api/client.ts'
import type { IdentityPurpose } from '../../api/types.ts'

type Props = {
  provider: string
  purpose: IdentityPurpose
  /** 본인 인증을 마치고 서버에서 받은 1회용 확인표 */
  onTicket: (ticket: string) => void
  onCancel: () => void
}

/**
 * 휴대폰 본인 인증 단계. 제공자마다 인증창이 다르다.
 * - fake: 개발·시험용. 연계정보와 생년월일을 직접 넣어 통과시킨다 (운영에서는 서버가 막는다).
 * - 실제 제공자: 인증창을 여는 연동이 아직 없다.
 */
export default function IdentityStep({ provider, purpose, onTicket, onCancel }: Props) {
  const [di, setDi] = useState('')
  const [birth, setBirth] = useState('1990-01-01')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (provider !== 'fake') {
    return (
      <div className="auth-form">
        <p className="notice notice--error" role="alert">
          본인 인증 연동이 아직 준비되지 않았어요.
        </p>
        <button type="button" className="btn btn--block" onClick={onCancel}>
          돌아가기
        </button>
      </div>
    )
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    if (!di.trim()) {
      setError('가상 인증에 쓸 값을 입력해 주세요.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const { ticket } = await verifyIdentity(purpose, `fake:${di.trim()}:${birth}:${Date.now()}`)
      onTicket(ticket)
    } catch (e) {
      setError(e instanceof Error ? e.message : '본인 인증에 실패했어요.')
      setBusy(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={submit} noValidate>
      <p className="notice" role="status">
        개발용 가상 본인 인증이에요. 실제 서비스에서는 휴대폰 본인 인증(PASS) 창이 열려요.
      </p>
      <label className="field-block">
        <span>사람 구분 값 (같은 값이면 같은 사람)</span>
        <input value={di} onChange={(event) => setDi(event.target.value)} autoComplete="off" />
      </label>
      <label className="field-block">
        <span>생년월일</span>
        <input type="date" value={birth} onChange={(event) => setBirth(event.target.value)} />
      </label>
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}
      <button type="submit" className="btn btn--on btn--block" disabled={busy}>
        {busy ? '확인하는 중…' : '본인 인증 완료'}
      </button>
      <div className="auth-form__links">
        <button type="button" className="link-btn" onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  )
}
