import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { approveBakeryReport, fetchBakeryReports, rejectBakeryReport, searchPlaces } from '../../api/admin.ts'
import type { AdminBakeryReport, PlaceCandidate } from '../../api/adminTypes.ts'
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
  const [placeQuery, setPlaceQuery] = useState(`${report.name ?? ''} ${report.address ?? ''}`.trim())
  const [candidates, setCandidates] = useState<PlaceCandidate[] | null>(null)
  const [chosen, setChosen] = useState<number | null>(null)
  const [searching, setSearching] = useState(false)
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
      // 카카오에서 위치를 자동으로 못 찾았으면 바로 후보 검색을 보여 준다.
      if (message.includes('찾지 못했어요')) void findPlaces()
    } finally {
      setBusy(false)
    }
  }

  async function findPlaces() {
    setSearching(true)
    try {
      const found = await searchPlaces(placeQuery.trim())
      setCandidates(found)
      setChosen(found.length > 0 ? 0 : null)
    } catch (e) {
      setCandidates([])
      setError(e instanceof Error ? e.message : '검색하지 못했어요.')
    } finally {
      setSearching(false)
    }
  }

  function approve() {
    const body: Parameters<typeof approveBakeryReport>[1] = { adminNote: note }
    if (report.kind === 'NEW' && candidates && chosen !== null) {
      body.place = candidates[chosen].place
    } else if (report.kind === 'NEW' && manualOpen && lat.trim() !== '' && lng.trim() !== '') {
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
            <div className="admin-place">
              <p className="card__meta">위치는 등록할 때 카카오에서 자동으로 찾아요. 못 찾으면 아래에서 검색해서 고를 수 있어요.</p>
              <div className="admin-row">
                <input
                  aria-label="위치 검색어"
                  className="admin-grow"
                  value={placeQuery}
                  placeholder="가게 이름이나 주소"
                  onChange={(e) => setPlaceQuery(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault()
                      void findPlaces()
                    }
                  }}
                />
                <button type="button" className="btn btn--small" disabled={searching || placeQuery.trim().length < 2} onClick={() => void findPlaces()}>
                  {searching ? '찾는 중…' : '카카오에서 검색'}
                </button>
              </div>
              {candidates && candidates.length === 0 && (
                <p className="notice">검색 결과가 없어요. 이름 대신 주소로 검색하거나, 아래 좌표 입력을 써 주세요.</p>
              )}
              {candidates && candidates.length > 0 && (
                <ul className="plain admin-candidates" role="radiogroup" aria-label="위치 후보">
                  {candidates.map((c, i) => (
                    <li key={`${c.place.kakaoPlaceId}-${i}`}>
                      <label className={chosen === i ? 'admin-candidate admin-candidate--on' : 'admin-candidate'}>
                        <input type="radio" name={`place-${report.id}`} checked={chosen === i} onChange={() => setChosen(i)} />
                        <span>
                          <strong>{c.place.name || '주소로 찾은 위치'}</strong>{' '}
                          <span className="card__meta">{c.category}</span>
                          <br />
                          <span className="card__meta">{c.place.roadAddress || c.place.jibunAddress}</span>
                        </span>
                      </label>
                    </li>
                  ))}
                </ul>
              )}
              <details open={manualOpen} onToggle={(e) => setManualOpen(e.currentTarget.open)}>
                <summary>그래도 안 되면 좌표로 직접 입력</summary>
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
            </div>
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
