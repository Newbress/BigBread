import { useState } from 'react'

type Props = {
  id: string
  value: string
  onChange: (value: string) => void
  autoComplete: 'current-password' | 'new-password'
  describedBy?: string
  invalid?: boolean
}

function EyeIcon({ off }: { off: boolean }) {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      {off ? (
        <>
          <path d="M17.94 17.94A10.94 10.94 0 0 1 12 19c-7 0-11-7-11-7a19.8 19.8 0 0 1 5.06-5.94M9.9 4.24A10.9 10.9 0 0 1 12 5c7 0 11 7 11 7a19.8 19.8 0 0 1-3.17 4.19M14.12 14.12a3 3 0 1 1-4.24-4.24" />
          <line x1="1" y1="1" x2="23" y2="23" />
        </>
      ) : (
        <>
          <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z" />
          <circle cx="12" cy="12" r="3" />
        </>
      )}
    </svg>
  )
}

/** 비밀번호 입력창. 안쪽 오른쪽의 눈 아이콘으로 보기/숨기기를 바꾼다. */
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
      <button
        type="button"
        className="password-input__toggle"
        aria-label={visible ? '비밀번호 숨기기' : '비밀번호 보기'}
        aria-pressed={visible}
        onClick={() => setVisible((current) => !current)}
      >
        <EyeIcon off={visible} />
      </button>
    </span>
  )
}
