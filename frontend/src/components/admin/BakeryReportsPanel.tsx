import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { approveBakeryReport, fetchBakeryReports, rejectBakeryReport } from '../../api/admin.ts'
import type { AdminBakeryReport } from '../../api/adminTypes.ts'
import { useAsync } from '../../hooks/useAsync.ts'
import { REPORT_KIND_LABEL, REPORT_STATUS_LABEL } from '../../lib/adminLabels.ts'
import { formatDateTime } from '../../lib/format.ts'
import { useAdmin } from './adminContext.ts'
import { Badge, ErrorNote, Pager } from './common.tsx'

const DISTRICTS = [
  { code: 'DONG', name: '동구' },
  { code: 'JUNG', name: '중구' },
  { code: 'SEO', name: '서구' },
  { code: 'YUSEONG', name: '유성구' },
  { code: 'DAEDEOK', name: '대덕구' },
]

const STATUS_TABS = [
  ['PENDING', '확인 중'],
  ['APPROVED', '승인'],
  ['REJECTED', '반려'],
] as const

function ReportCard({ report, onDone }: { report: AdminBakeryReport; onDone: () => void }) {
  const { refreshSummary } = useAdmin()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [manualOpen, setManualOpen] = useState(false)
  const [lat, setLat] = useState('')
  const [lng, setLng] = useState('')
  const [district, setDistrict] = useState('YUSEONG')
  const [applyInstagram, setApplyInstagram] = useState(false)
  const [note, setNote] = useState('')
  const [rejectNote, setRejectNote] = useState('')
  const [rejecting, setRejecting] = useState(false)

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      refreshSummary()
      onDone()
    } catch (e) {
      const message = e instanceof Error ? e.message : '처리하지 못했어요.'
      setError(message)
      if (message.includes('직접')) setManualOpen(true)
    } finally {
      setBusy(false)
    }
  }

  function approve() {
    const body: Parameters<typeof approveBakeryReport>[1] = { adminNote: note }
    if (report.kind === 'NEW' && manualOpen && lat.trim() !== '' && lng.trim() !== '') {
      body.lat = Number(lat)
      body.lng = Number(lng)
      body.districtCode = district
    }
    if (report.kind === 'WRONG_INFO') body.applyInstagram = applyInstagram
    void run(() => approveBakeryReport(report.id, body))
  }

  const title = report.kind === 'NEW' ? report.name : (report.bakeryName ?? '(삭제된 빵집)')
  const approveLabel = report.kind === 'NEW' ? '빵집으로 등록' : report.kind === 'CLOSED' ? '폐업 처리' : '확인 완료'

  return (
    <li className="admin-item">
      <div className="admin-item__head">
        <Badge tone={report.kind === 'NEW' ? 'ok' : 'warn'}>{REPORT_KIND_LABEL[report.kind]}</Badge>
        <strong>{title}</strong>
        {report.status !== 'PENDING' && (
          <Badge tone={report.status === 'APPROVED' ? 'ok' : 'danger'}>{REPORT_STATUS_LABEL[report.status]}</Badge>
        )}
      </div>
      {report.address && <p className="card__meta">{report.address}</p>}
      {report.instagramUsername && (
        <p className="card__meta">
          인스타그램{' '}
          <a href={`https://www.instagram.com/${report.instagramUsername}/`} target="_blank" rel="noopener noreferrer">
            @{report.instagramUsername}
          </a>
        </p>
      )}
      {report.note && <p className="admin-item__note">{report.note}</p>}
      <p className="card__meta">
        {report.reporterNickname} (사용자 {report.reporterId}) · {formatDateTime(report.createdAt)}
        {report.reviewedAt && ` · 처리 ${formatDateTime(report.reviewedAt)}`}
      </p>
      {report.adminNote && <p className="card__meta">운영자 안내: {report.adminNote}</p>}
      {report.bakeryId && report.status === 'APPROVED' && report.kind === 'NEW' && (
        <p className="card__meta">등록된 빵집 번호 {report.bakeryId}</p>
      )}

      {report.status === 'PENDING' && (
        <div className="admin-actions">
          {report.kind === 'NEW' && (
            <details open={manualOpen} onToggle={(e) => setManualOpen(e.currentTarget.open)}>
              <summary>위치를 직접 넣기 (카카오에서 못 찾을 때)</summary>
              <div className="admin-row">
                <label>
                  위도
                  <input inputMode="decimal" value={lat} placeholder="36.35" onChange={(e) => setLat(e.target.value)} />
                </label>
                <label>
                  경도
                  <input inputMode="decimal" value={lng} placeholder="127.38" onChange={(e) => setLng(e.target.value)} />
                </label>
                <label>
                  구
                  <select value={district} onChange={(e) => setDistrict(e.target.value)}>
                    {DISTRICTS.map((d) => (
                      <option key={d.code} value={d.code}>
                        {d.name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </details>
          )}
          {report.kind === 'WRONG_INFO' && (
            <>
              {report.instagramUsername && (
                <label className="admin-check">
                  <input type="checkbox" checked={applyInstagram} onChange={(e) => setApplyInstagram(e.target.checked)} />
                  제보된 인스타 계정(@{report.instagramUsername})을 빵집에 반영
                </label>
              )}
              <p className="card__meta">
                나머지 정보는{' '}
                <Link to={`/admin/bakeries?q=${encodeURIComponent(report.bakeryName ?? '')}`}>빵집 관리</Link>에서 고친 뒤 확인 완료를 눌러 주세요.
              </p>
            </>
          )}
          <label className="admin-field">
            제보한 사람에게 보일 안내 (선택)
            <input value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} />
          </label>
          <div className="admin-row">
            <button type="button" className="btn btn--small btn--primary" disabled={busy} onClick={approve}>
              {busy ? '처리 중…' : approveLabel}
            </button>
            {rejecting ? (
              <>
                <input
                  aria-label="반려 사유"
                  className="admin-grow"
                  value={rejectNote}
                  maxLength={500}
                  placeholder="반려 사유 (제보한 사람에게 보여요)"
                  onChange={(e) => setRejectNote(e.target.value)}
                />
                <button
                  type="button"
                  className="btn btn--small btn--danger"
                  disabled={busy || rejectNote.trim() === ''}
                  onClick={() => void run(() => rejectBakeryReport(report.id, rejectNote))}
                >
                  반려하기
                </button>
                <button type="button" className="btn btn--small" onClick={() => setRejecting(false)}>
                  취소
                </button>
              </>
            ) : (
              <button type="button" className="btn btn--small" disabled={busy} onClick={() => setRejecting(true)}>
                반려
              </button>
            )}
          </div>
        </div>
      )}
      <ErrorNote message={error} />
    </li>
  )
}

export default function BakeryReportsPanel() {
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? 'PENDING'
  const [page, setPage] = useState(0)
  const { data, loading, error, reload } = useAsync((signal) => fetchBakeryReports(status, page, signal), `${status}:${page}`)

  return (
    <section aria-labelledby="br-title">
      <h2 id="br-title">빵집 제보</h2>
      <div className="admin-tabs" role="tablist">
        {STATUS_TABS.map(([value, label]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={status === value}
            className={status === value ? 'btn btn--small btn--toggle btn--on' : 'btn btn--small btn--toggle'}
            onClick={() => {
              setPage(0)
              setParams(value === 'PENDING' ? {} : { status: value })
            }}
          >
            {label}
          </button>
        ))}
      </div>
      <ErrorNote message={error} />
      {loading && !data && <p className="summary">불러오는 중…</p>}
      {data && data.items.length === 0 && <p className="notice">{status === 'PENDING' ? '확인할 제보가 없어요. 👏' : '제보가 없어요.'}</p>}
      <ul className="plain admin-list" aria-busy={loading}>
        {data?.items.map((report) => <ReportCard key={report.id} report={report} onDone={reload} />)}
      </ul>
      {data && <Pager page={page} totalPages={data.totalPages} onChange={setPage} />}
    </section>
  )
}
