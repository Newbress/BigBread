import { useState } from 'react'

type Props = {
  id: string
  value: string
  onChange: (value: string) => void
  autoComplete: 'current-password' | 'new-password'
  describedBy?: string
  invalid?: boolean
}

/** 비밀번호 입력창 (보기/숨기기 전환). */
export default function PasswordInput({ id, value, onChange, autoComplete, describedBy, invalid }: Props) {
  const [visible, setVisible] = useState(false)
  return (
    <span className="password-input">
      <input
        id={id}
        type={visible ? 'text' : 'password'}
        value={value}
        autoComplete={autoComplete}
        aria-describedby={describedBy}
        aria-invalid={invalid || undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      <button type="button" className="btn btn--small" aria-pressed={visible} onClick={() => setVisible((v) => !v)}>
        {visible ? '숨기기' : '보기'}
      </button>
    </span>
  )
}
