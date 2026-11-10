import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { fetchContentReports, resolveContentReport } from '../../api/admin.ts'
import type { AdminContentGroup, ContentAction } from '../../api/adminTypes.ts'
import { useAsync } from '../../hooks/useAsync.ts'
import { REPORT_REASON_LABEL } from '../../lib/adminLabels.ts'
import { formatDateTime } from '../../lib/format.ts'
import { useAdmin } from './adminContext.ts'
import { Badge, ConfirmButton, ErrorNote, Pager } from './common.tsx'

function GroupCard({ group, handled, onDone }: { group: AdminContentGroup; handled: boolean; onDone: () => void }) {
  const { refreshSummary } = useAdmin()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { target } = group
  const hidden = target.status === 'HIDDEN'

  async function act(action: ContentAction) {
    setBusy(true)
    setError(null)
    try {
      await resolveContentReport(group.targetType, group.targetId, action)
      refreshSummary()
      onDone()
    } catch (e) {
      setError(e instanceof Error ? e.message : '처리하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className="admin-item">
      <div className="admin-item__head">
        <Badge tone="warn">{group.targetType === 'REVIEW' ? '리뷰' : '사진'}</Badge>
        {!handled && <Badge tone="danger">신고 {group.pendingCount}건</Badge>}
        {hidden && <Badge tone="danger">{target.hiddenReason === 'ADMIN' ? '운영자가 숨김' : '신고 누적으로 자동 숨김'}</Badge>}
        {!target.exists && <Badge>이미 삭제됨</Badge>}
        {group.reasons.map((reason) => (
          <Badge key={reason}>{REPORT_REASON_LABEL[reason]}</Badge>
        ))}
      </div>

      {target.exists ? (
        <div className="admin-target">
          <p className="card__meta">
            {target.bakeryName} · 작성자{' '}
            <Link to={`/admin/users?q=${target.authorId}`}>
              {target.authorNickname} (사용자 {target.authorId})
            </Link>
            {target.rating !== null && ` · ★${target.rating}`}
          </p>
          {target.content && <p className="admin-item__note">{target.content}</p>}
          {target.photos.length > 0 && (
            <ul className="plain admin-photos">
              {target.photos.map((photo) => (
                <li key={photo.id} className={photo.id === group.targetId && group.targetType === 'PHOTO' ? 'admin-photos__target' : undefined}>
                  <a href={photo.url} target="_blank" rel="noopener noreferrer" title={photo.status === 'HIDDEN' ? '숨겨진 사진' : '사진 크게 보기'}>
                    <img src={photo.thumbUrl} alt={photo.status === 'HIDDEN' ? '숨겨진 사진' : '리뷰 사진'} loading="lazy" />
                  </a>
                  {photo.status === 'HIDDEN' && <span className="admin-photos__hidden">숨김</span>}
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <p className="card__meta">대상이 이미 지워졌어요.</p>
      )}

      <details>
        <summary>
          신고 내용 {group.totalCount}건 · 처음 {formatDateTime(group.firstReportedAt)}
        </summary>
        <ul className="plain admin-details">
          {group.details.map((d, i) => (
            <li key={i}>
              <strong>{REPORT_REASON_LABEL[d.reason]}</strong> · {d.reporterNickname} · {formatDateTime(d.createdAt)}
              {d.detail && <span className="admin-item__note"> {d.detail}</span>}
            </li>
          ))}
        </ul>
      </details>

      <div className="admin-row admin-actions">
        {!handled && (
          <button type="button" className="btn btn--small btn--primary" disabled={busy} onClick={() => void act('DISMISS')}>
            문제없음{hidden && target.hiddenReason === 'REPORTS' ? ' (다시 보이기)' : ''}
          </button>
        )}
        {!handled && target.exists && !hidden && (
          <button type="button" className="btn btn--small" disabled={busy} onClick={() => void act('HIDE')}>
            숨기기
          </button>
        )}
        {!handled && target.exists && hidden && (
          <button type="button" className="btn btn--small" disabled={busy} onClick={() => void act('HIDE')}>
            숨김 유지
          </button>
        )}
        {handled && hidden && (
          <button type="button" className="btn btn--small btn--primary" disabled={busy} onClick={() => void act('RESTORE')}>
            숨김 해제
          </button>
        )}
        {target.exists && (
          <ConfirmButton
            label="삭제"
            confirmLabel="정말 삭제"
            disabled={busy}
            onConfirm={() => void act('DELETE')}
          />
        )}
        {!target.exists && !handled && (
          <button type="button" className="btn btn--small" disabled={busy} onClick={() => void act('DISMISS')}>
            신고 닫기
          </button>
        )}
      </div>
      <ErrorNote message={error} />
    </li>
  )
}

export default function ContentReportsPanel() {
  const [params, setParams] = useSearchParams()
  const handled = params.get('status') === 'HANDLED'
  const [page, setPage] = useState(0)
  const status = handled ? 'HANDLED' : 'PENDING'
  const { data, loading, error, reload } = useAsync((signal) => fetchContentReports(status, page, signal), `${status}:${page}`)

  return (
    <section aria-labelledby="cr-title">
      <h2 id="cr-title">리뷰·사진 신고</h2>
      <div className="admin-tabs" role="tablist">
        {(
          [
            ['PENDING', '처리 전'],
            ['HANDLED', '처리됨'],
          ] as const
        ).map(([value, label]) => (
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
      {data && data.items.length === 0 && <p className="notice">{handled ? '처리된 신고가 없어요.' : '처리할 신고가 없어요. 👏'}</p>}
      <ul className="plain admin-list" aria-busy={loading}>
        {data?.items.map((group) => (
          <GroupCard key={`${group.targetType}-${group.targetId}`} group={group} handled={handled} onDone={reload} />
        ))}
      </ul>
      {data && <Pager page={page} totalPages={data.totalPages} onChange={setPage} />}
    </section>
  )
}
