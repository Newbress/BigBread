import { useState } from 'react'
import { reportContent } from '../api/client.ts'
import type { ReportReason, ReportTarget } from '../api/types.ts'

const REASONS: { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: '광고·도배' },
  { value: 'ABUSE', label: '욕설·비방' },
  { value: 'PRIVACY', label: '개인정보 노출' },
  { value: 'INAPPROPRIATE', label: '부적절한 내용' },
  { value: 'OTHER', label: '기타' },
]

type Props = {
  targetType: ReportTarget
  targetId: number
  onDone: () => void
  onCancel: () => void
}

/** 리뷰·사진 신고 양식. 접수되면 onDone, 신고자는 작성자에게 알려지지 않는다. */
export default function ContentReportForm({ targetType, targetId, onDone, onCancel }: Props) {
  const [reason, setReason] = useState<ReportReason>('SPAM')
  const [detail, setDetail] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (reason === 'OTHER' && detail.trim() === '') {
      setError("'기타'를 고르면 내용을 적어 주세요.")
      return
    }
    setSending(true)
    setError(null)
    try {
      const res = await reportContent(targetType, targetId, reason, detail)
      setDone(res.message)
    } catch (e) {
      setError(e instanceof Error ? e.message : '신고하지 못했어요.')
    } finally {
      setSending(false)
    }
  }

  if (done) {
    return (
      <div className="report-form">
        <p className="notice" role="status">
          {done}
        </p>
        <button type="button" className="btn btn--small" onClick={onDone}>
          닫기
        </button>
      </div>
    )
  }

  const idPrefix = `report-${targetType}-${targetId}`
  return (
    <form className="report-form" onSubmit={submit}>
      <label htmlFor={`${idPrefix}-reason`}>신고 사유</label>
      <select id={`${idPrefix}-reason`} value={reason} onChange={(e) => setReason(e.target.value as ReportReason)}>
        {REASONS.map((r) => (
          <option key={r.value} value={r.value}>
            {r.label}
          </option>
        ))}
      </select>
      <label htmlFor={`${idPrefix}-detail`}>자세한 내용 {reason === 'OTHER' ? '' : '(선택)'}</label>
      <textarea
        id={`${idPrefix}-detail`}
        rows={3}
        maxLength={500}
        value={detail}
        onChange={(e) => setDetail(e.target.value)}
      />
      <p className="card__meta">신고한 사람은 작성자에게 알려지지 않아요. 여러 명이 신고하면 확인될 때까지 잠시 숨겨져요.</p>
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}
      <div className="detail__links">
        <button type="submit" className="btn btn--small btn--danger" disabled={sending}>
          {sending ? '보내는 중…' : '신고하기'}
        </button>
        <button type="button" className="btn btn--small" disabled={sending} onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  )
}
