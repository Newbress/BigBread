import { useEffect, useRef, useState } from 'react'
import { createBakeryReport, fetchMyBakeryReports } from '../api/client.ts'
import type { BakeryReport, BakeryReportKind } from '../api/types.ts'

export type ReportBakeryTarget = { id: number; name: string } | null

type Props = {
  /** 기존 빵집에 대한 제보면 그 빵집, 새 빵집 제보면 null */
  bakery: ReportBakeryTarget
  onClose: () => void
}

const KIND_LABEL: Record<BakeryReportKind, string> = {
  NEW: '새 빵집',
  CLOSED: '폐업·이전',
  WRONG_INFO: '정보 오류',
}

const STATUS_LABEL: Record<BakeryReport['status'], string> = {
  PENDING: '확인 중',
  APPROVED: '반영됨',
  REJECTED: '반영 안 됨',
}

function describe(report: BakeryReport): string {
  if (report.kind === 'NEW') return report.name ?? ''
  return report.bakeryName ?? '(삭제된 빵집)'
}

/** 빵집 제보 창: 새 빵집 / 폐업·이전 / 정보 오류를 보내고, 내가 보낸 제보의 처리 결과를 본다. */
export default function ReportBakeryDialog({ bakery, onClose }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const [kind, setKind] = useState<BakeryReportKind>(bakery ? 'WRONG_INFO' : 'NEW')
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')
  const [instagram, setInstagram] = useState('')
  const [note, setNote] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)
  const [history, setHistory] = useState<BakeryReport[]>([])
  const [reload, setReload] = useState(0)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    fetchMyBakeryReports(controller.signal)
      .then(setHistory)
      .catch(() => {
        // 내역을 못 불러와도 제보는 보낼 수 있다.
      })
    return () => controller.abort()
  }, [reload])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSending(true)
    setError(null)
    try {
      await createBakeryReport(
        kind === 'NEW'
          ? { kind, name, address, instagram, note }
          : { kind, bakeryId: bakery?.id, instagram, note },
      )
      setSent(true)
      setName('')
      setAddress('')
      setInstagram('')
      setNote('')
      setReload((n) => n + 1)
    } catch (e) {
      setError(e instanceof Error ? e.message : '제보를 보내지 못했어요.')
    } finally {
      setSending(false)
    }
  }

  return (
    <dialog ref={ref} className="login-dialog report-dialog" aria-labelledby="report-title" onClose={onClose}>
      <div className="report-dialog__body">
        <button type="button" className="login-dialog__close" aria-label="닫기" onClick={() => ref.current?.close()}>
          ×
        </button>
        <h2 id="report-title">{bakery ? `${bakery.name} 제보` : '빵집 제보하기'}</h2>

        {sent ? (
          <>
            <p className="notice" role="status">
              제보를 받았어요. 운영자가 확인한 뒤 반영할게요. 결과는 아래 내역에서 볼 수 있어요.
            </p>
            <button type="button" className="btn btn--small" onClick={() => setSent(false)}>
              하나 더 제보하기
            </button>
          </>
        ) : (
          <form className="report-form" onSubmit={submit}>
            {bakery ? (
              <>
                <label htmlFor="bakery-report-kind">무엇이 문제인가요?</label>
                <select id="bakery-report-kind" value={kind} onChange={(e) => setKind(e.target.value as BakeryReportKind)}>
                  <option value="WRONG_INFO">정보가 달라요 (주소·전화·인스타 등)</option>
                  <option value="CLOSED">폐업했거나 이전했어요</option>
                </select>
              </>
            ) : (
              <>
                <label htmlFor="bakery-report-name">빵집 이름</label>
                <input id="bakery-report-name" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} required />
                <label htmlFor="bakery-report-address">주소</label>
                <input
                  id="bakery-report-address"
                  value={address}
                  maxLength={255}
                  placeholder="예) 대전 유성구 …"
                  onChange={(e) => setAddress(e.target.value)}
                  required
                />
              </>
            )}
            {kind !== 'CLOSED' && (
              <>
                <label htmlFor="bakery-report-instagram">인스타그램 계정 (선택)</label>
                <input
                  id="bakery-report-instagram"
                  value={instagram}
                  maxLength={100}
                  placeholder="@계정 또는 인스타그램 주소"
                  onChange={(e) => setInstagram(e.target.value)}
                />
              </>
            )}
            <label htmlFor="bakery-report-note">
              {kind === 'WRONG_INFO' ? '어떤 정보가 다른가요?' : kind === 'CLOSED' ? '자세한 내용 (선택)' : '하고 싶은 말 (선택)'}
            </label>
            <textarea id="bakery-report-note" rows={3} maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} />
            {error && (
              <p className="notice notice--error" role="alert">
                {error}
              </p>
            )}
            <button type="submit" className="btn btn--primary" disabled={sending}>
              {sending ? '보내는 중…' : '제보 보내기'}
            </button>
          </form>
        )}

        {history.length > 0 && (
          <section aria-labelledby="my-reports-title" className="report-history">
            <h3 id="my-reports-title">내 제보 내역</h3>
            <ul className="plain">
              {history.map((report) => (
                <li key={report.id}>
                  <strong>{describe(report)}</strong>
                  <span className="card__meta">
                    {' '}
                    · {KIND_LABEL[report.kind]} · {STATUS_LABEL[report.status]} ·{' '}
                    {new Date(report.createdAt).toLocaleDateString('ko-KR')}
                  </span>
                  {report.adminNote && <p className="card__meta">운영자 안내: {report.adminNote}</p>}
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </dialog>
  )
}
