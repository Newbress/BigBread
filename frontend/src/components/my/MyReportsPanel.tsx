import { fetchMyBakeryReports, fetchMyContentReports } from '../../api/client.ts'
import type { BakeryReport, MyContentReport } from '../../api/types.ts'
import { useAsync } from '../../hooks/useAsync.ts'
import { REPORT_KIND_LABEL, REPORT_REASON_LABEL } from '../../lib/adminLabels.ts'
import { formatDateTime } from '../../lib/format.ts'
import { Badge, ErrorNote } from '../admin/common.tsx'

const BAKERY_STATUS: Record<BakeryReport['status'], { label: string; tone: 'plain' | 'ok' | 'danger' }> = {
  PENDING: { label: '확인 중', tone: 'plain' },
  APPROVED: { label: '반영됨', tone: 'ok' },
  REJECTED: { label: '반영 안 됨', tone: 'danger' },
}

const CONTENT_STATUS: Record<MyContentReport['status'], { label: string; tone: 'plain' | 'ok' | 'danger' }> = {
  PENDING: { label: '확인 중', tone: 'plain' },
  DISMISSED: { label: '문제없음으로 처리', tone: 'plain' },
  ACTIONED: { label: '조치됨', tone: 'ok' },
}

/** 내가 보낸 빵집 제보. */
export function MyBakeryReportsPanel() {
  const { data, loading, error } = useAsync((signal) => fetchMyBakeryReports(signal), 'bakery-reports')
  return (
    <section aria-labelledby="my-br-title">
      <h2 id="my-br-title">내 제보</h2>
      <ErrorNote message={error} />
      {loading && !data && <p className="summary">불러오는 중…</p>}
      {data && data.length === 0 && <p className="notice">보낸 제보가 없어요. 목록 아래 &lsquo;빵집 제보하기&rsquo;로 새 빵집을 알려 주세요.</p>}
      <ul className="plain my-list" aria-busy={loading}>
        {data?.map((report) => {
          const status = BAKERY_STATUS[report.status]
          return (
            <li key={report.id} className="my-item">
              <div className="my-item__head">
                <Badge>{REPORT_KIND_LABEL[report.kind]}</Badge>
                <strong>{report.kind === 'NEW' ? report.name : (report.bakeryName ?? '(삭제된 빵집)')}</strong>
                <Badge tone={status.tone}>{status.label}</Badge>
              </div>
              {report.address && <p className="card__meta">{report.address}</p>}
              {report.note && <p className="review__content">{report.note}</p>}
              {report.adminNote && <p className="notice">운영자 안내: {report.adminNote}</p>}
              <p className="card__meta">
                {formatDateTime(report.createdAt)}
                {report.reviewedAt && ` · 처리 ${formatDateTime(report.reviewedAt)}`}
              </p>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

/** 내가 한 리뷰·사진 신고. 신고한 사람은 작성자에게 알려지지 않는다. */
export function MyContentReportsPanel() {
  const { data, loading, error } = useAsync((signal) => fetchMyContentReports(signal), 'content-reports')
  return (
    <section aria-labelledby="my-cr-title">
      <h2 id="my-cr-title">내 신고</h2>
      <p className="card__meta">신고한 사람은 작성자에게 알려지지 않아요.</p>
      <ErrorNote message={error} />
      {loading && !data && <p className="summary">불러오는 중…</p>}
      {data && data.length === 0 && <p className="notice">한 신고가 없어요.</p>}
      <ul className="plain my-list" aria-busy={loading}>
        {data?.map((report) => {
          const status = CONTENT_STATUS[report.status]
          return (
            <li key={report.id} className="my-item">
              <div className="my-item__head">
                <Badge tone="warn">{report.targetType === 'REVIEW' ? '리뷰' : '사진'}</Badge>
                <strong>{report.bakeryName ?? '삭제된 글'}</strong>
                <Badge tone={status.tone}>{status.label}</Badge>
              </div>
              {report.excerpt && <p className="review__content">“{report.excerpt}”</p>}
              {report.targetGone && <p className="card__meta">신고한 글이 이미 지워졌어요.</p>}
              <p className="card__meta">
                사유: {REPORT_REASON_LABEL[report.reason]}
                {report.detail && ` — ${report.detail}`}
              </p>
              <p className="card__meta">
                {formatDateTime(report.createdAt)}
                {report.handledAt && ` · 처리 ${formatDateTime(report.handledAt)}`}
              </p>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
