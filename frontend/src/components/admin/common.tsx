import { useState } from 'react'
import type { ReactNode } from 'react'

export function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <nav className="admin-pager" aria-label="페이지 이동">
      <button type="button" className="btn btn--small" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        이전
      </button>
      <span>
        {page + 1} / {totalPages}
      </span>
      <button type="button" className="btn btn--small" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        다음
      </button>
    </nav>
  )
}

export function Badge({ tone = 'plain', children }: { tone?: 'plain' | 'warn' | 'ok' | 'danger'; children: ReactNode }) {
  return <span className={`admin-badge admin-badge--${tone}`}>{children}</span>
}

/** 한 번 눌러 확인을 띄우고, 다시 눌러야 실행되는 위험한 동작 버튼. */
export function ConfirmButton({
  label,
  confirmLabel,
  onConfirm,
  disabled,
  danger = true,
}: {
  label: string
  confirmLabel: string
  onConfirm: () => void
  disabled?: boolean
  danger?: boolean
}) {
  const [asking, setAsking] = useState(false)
  if (!asking) {
    return (
      <button type="button" className="btn btn--small" disabled={disabled} onClick={() => setAsking(true)}>
        {label}
      </button>
    )
  }
  return (
    <span className="admin-confirm">
      <button
        type="button"
        className={danger ? 'btn btn--small btn--danger' : 'btn btn--small btn--primary'}
        disabled={disabled}
        onClick={() => {
          setAsking(false)
          onConfirm()
        }}
      >
        {confirmLabel}
      </button>
      <button type="button" className="btn btn--small" onClick={() => setAsking(false)}>
        취소
      </button>
    </span>
  )
}

export function ErrorNote({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <p className="notice notice--error" role="alert">
      {message}
    </p>
  )
}
