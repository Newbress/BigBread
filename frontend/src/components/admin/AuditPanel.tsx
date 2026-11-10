import { useState } from 'react'
import { fetchAuditLogs } from '../../api/admin.ts'
import { useAsync } from '../../hooks/useAsync.ts'
import { AUDIT_TARGET_LABEL, auditActionLabel } from '../../lib/adminLabels.ts'
import { formatDateTime } from '../../lib/format.ts'
import { ErrorNote, Pager } from './common.tsx'

export default function AuditPanel() {
  const [targetType, setTargetType] = useState('')
  const [page, setPage] = useState(0)
  const { data, loading, error } = useAsync((signal) => fetchAuditLogs(targetType, page, signal), `${targetType}|${page}`)

  return (
    <section aria-labelledby="au-title">
      <h2 id="au-title">작업 기록</h2>
      <p className="card__meta">운영자가 한 모든 처리가 남아요. 기록은 읽기만 되고 고치거나 지울 수 없어요.</p>
      <label className="admin-field admin-field--inline">
        종류
        <select
          value={targetType}
          onChange={(e) => {
            setPage(0)
            setTargetType(e.target.value)
          }}
        >
          <option value="">전체</option>
          {Object.entries(AUDIT_TARGET_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <ErrorNote message={error} />
      {loading && !data && <p className="summary">불러오는 중…</p>}
      {data && data.items.length === 0 && <p className="notice">기록이 없어요.</p>}
      <ul className="plain admin-list" aria-busy={loading}>
        {data?.items.map((e) => (
          <li key={e.id} className="admin-item">
            <p>
              <strong>{auditActionLabel(e.action)}</strong>{' '}
              <span className="card__meta">
                {AUDIT_TARGET_LABEL[e.targetType] ?? e.targetType}
                {e.targetId !== null && ` ${e.targetId}`}
              </span>
            </p>
            {e.detail && <p className="admin-item__note">{e.detail}</p>}
            <p className="card__meta">
              {e.adminNickname} · {formatDateTime(e.createdAt)}
            </p>
          </li>
        ))}
      </ul>
      {data && <Pager page={page} totalPages={data.totalPages} onChange={setPage} />}
    </section>
  )
}
