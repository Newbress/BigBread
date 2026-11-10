import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { fetchAdminUser, hideUserContent, resetUserPassword, searchAdminUsers, suspendUser, unsuspendUser } from '../../api/admin.ts'
import type { AdminUserDetail } from '../../api/adminTypes.ts'
import { useAsync } from '../../hooks/useAsync.ts'
import { formatDateTime } from '../../lib/format.ts'
import { useAdmin } from './adminContext.ts'
import { Badge, ConfirmButton, ErrorNote, Pager } from './common.tsx'

function UserDetail({ detail, onChanged }: { detail: AdminUserDetail; onChanged: () => void }) {
  const { refreshSummary } = useAdmin()
  const { user } = detail
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [temp, setTemp] = useState<string | null>(null)

  async function issueTemporaryPassword() {
    setBusy(true)
    setError(null)
    try {
      const result = await resetUserPassword(user.id)
      setTemp(result.temporaryPassword)
    } catch (e) {
      setError(e instanceof Error ? e.message : '임시 비밀번호를 만들지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      setReason('')
      refreshSummary()
      onChanged()
    } catch (e) {
      setError(e instanceof Error ? e.message : '처리하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  const suspended = user.suspendedAt !== null
  return (
    <div className="admin-editor">
      <h3>
        {user.nickname} <span className="card__meta">사용자 {user.id}</span>
      </h3>
      <p className="detail__links">
        {user.role === 'ADMIN' && <Badge tone="warn">관리자</Badge>}
        {suspended && <Badge tone="danger">정지됨</Badge>}
        {user.providers.map((p) => (
          <Badge key={p}>{p}</Badge>
        ))}
        {user.loginId && <Badge>자체 로그인 {user.loginId}</Badge>}
      </p>
      <p className="card__meta">
        {user.maskedEmail ?? '이메일 없음'} · 가입 {formatDateTime(user.createdAt)} · 리뷰 {user.reviewCount}개 · 사진 {user.photoCount}장 · 받은 신고{' '}
        {user.reportsReceived}건
      </p>
      {suspended && (
        <p className="notice" role="status">
          {formatDateTime(user.suspendedAt)}에 정지됨 — 사유: {user.suspendedReason}
        </p>
      )}

      {user.role !== 'ADMIN' && (
        <div className="admin-actions">
          <label className="admin-field">
            사유 {suspended ? '' : '(정지·글 숨김에 필요해요)'}
            <input value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} />
          </label>
          <div className="admin-row">
            {suspended ? (
              <button type="button" className="btn btn--small btn--primary" disabled={busy} onClick={() => void run(() => unsuspendUser(user.id))}>
                정지 해제
              </button>
            ) : (
              <ConfirmButton
                label="정지"
                confirmLabel="정말 정지"
                disabled={busy || reason.trim() === ''}
                onConfirm={() => void run(() => suspendUser(user.id, reason))}
              />
            )}
            <ConfirmButton
              label="글 모두 숨기기"
              confirmLabel="정말 모두 숨기기"
              disabled={busy || reason.trim() === '' || user.reviewCount === 0}
              onConfirm={() => void run(() => hideUserContent(user.id, reason))}
            />
          </div>
          <p className="card__meta">정지하면 로그인·조회는 되지만 리뷰·사진·제보·신고를 쓸 수 없어요. 숨긴 글은 신고 화면 &lsquo;처리됨&rsquo;에서 하나씩 풀 수 있어요.</p>
        </div>
      )}
      {user.role !== 'ADMIN' && user.loginId && (
        <div className="admin-actions">
          <ConfirmButton
            label="임시 비밀번호 발급"
            confirmLabel="정말 발급 (기존 비밀번호·로그인 해제)"
            disabled={busy}
            onConfirm={() => void issueTemporaryPassword()}
          />
          <p className="card__meta">비밀번호를 잊은 이용자용이에요. 발급하면 모든 기기의 로그인이 풀리고, 새 비밀번호는 아래에 한 번만 보여요.</p>
          {temp && (
            <p className="notice" role="status">
              임시 비밀번호: <code>{temp}</code> — 이용자에게 전달하고 로그인 후 바로 바꾸도록 안내해 주세요. 이 창을 닫으면 다시 볼 수 없어요.
            </p>
          )}
        </div>
      )}
      <ErrorNote message={error} />

      <h4>최근 리뷰</h4>
      {detail.recentReviews.length === 0 ? (
        <p className="notice">쓴 리뷰가 없어요.</p>
      ) : (
        <ul className="plain admin-details">
          {detail.recentReviews.map((r) => (
            <li key={r.id}>
              <Link to={`/admin/bakeries?q=${encodeURIComponent(r.bakeryName)}`}>{r.bakeryName}</Link> · ★{r.rating} ·{' '}
              {formatDateTime(r.createdAt)} {r.status === 'HIDDEN' && <Badge tone="danger">숨김</Badge>}
              {r.content && <span className="admin-item__note"> {r.content}</span>}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default function UsersPanel() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const suspendedOnly = params.get('suspended') === '1'
  const [text, setText] = useState(q)
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const list = useAsync((signal) => searchAdminUsers(q, suspendedOnly, page, signal), `${q}|${suspendedOnly}|${page}`)
  const detail = useAsync(
    (signal) => (selected === null ? Promise.resolve(null) : fetchAdminUser(selected, signal)),
    String(selected),
  )

  function update(nextQ: string, nextSuspended: boolean) {
    setPage(0)
    setParams({ ...(nextQ ? { q: nextQ } : {}), ...(nextSuspended ? { suspended: '1' } : {}) })
  }

  return (
    <section aria-labelledby="us-title">
      <h2 id="us-title">사용자 관리</h2>
      <form
        className="admin-row"
        role="search"
        onSubmit={(e) => {
          e.preventDefault()
          update(text.trim(), suspendedOnly)
        }}
      >
        <input
          aria-label="사용자 검색"
          className="admin-grow"
          value={text}
          placeholder="닉네임·아이디·사용자 번호·이메일(전체)"
          onChange={(e) => setText(e.target.value)}
        />
        <label className="admin-check">
          <input type="checkbox" checked={suspendedOnly} onChange={(e) => update(q, e.target.checked)} />
          정지된 사용자만
        </label>
        <button type="submit" className="btn btn--small">
          검색
        </button>
      </form>
      <ErrorNote message={list.error} />
      {list.data && <p className="summary">{list.data.totalElements}명</p>}
      <ul className="plain admin-list" aria-busy={list.loading}>
        {list.data?.items.map((u) => (
          <li key={u.id}>
            <button
              type="button"
              className={selected === u.id ? 'admin-row-btn admin-row-btn--on' : 'admin-row-btn'}
              onClick={() => setSelected(u.id)}
            >
              <span>
                <strong>{u.nickname}</strong> <span className="card__meta">사용자 {u.id}</span>
              </span>
              <span className="admin-row-btn__meta">
                {u.role === 'ADMIN' && <Badge tone="warn">관리자</Badge>}
                {u.suspendedAt && <Badge tone="danger">정지됨</Badge>}
                <span>리뷰 {u.reviewCount}</span>
                {u.reportsReceived > 0 && <span>신고 {u.reportsReceived}</span>}
              </span>
            </button>
          </li>
        ))}
      </ul>
      {list.data && <Pager page={page} totalPages={list.data.totalPages} onChange={setPage} />}

      {selected !== null && (
        <div className="admin-detail">
          <ErrorNote message={detail.error} />
          {detail.loading && !detail.data && <p className="summary">불러오는 중…</p>}
          {detail.data && (
            <UserDetail
              key={`${detail.data.user.id}-${detail.data.user.suspendedAt}-${detail.data.user.reviewCount}`}
              detail={detail.data}
              onChanged={() => {
                detail.reload()
                list.reload()
              }}
            />
          )}
        </div>
      )}
    </section>
  )
}
