import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { fetchAdminBakery, searchAdminBakeries, updateAdminBakery } from '../../api/admin.ts'
import type { AdminBakeryDetail, BakeryStatus } from '../../api/adminTypes.ts'
import { useAsync } from '../../hooks/useAsync.ts'
import { BAKERY_STATUS_LABEL } from '../../lib/adminLabels.ts'
import { formatRating } from '../../lib/format.ts'
import { DAYS, toHoursPayload, toHoursState, type DayState, type HoursState } from '../../lib/openingHours.ts'
import { Badge, ErrorNote, Pager } from './common.tsx'

function Editor({ detail, onSaved }: { detail: AdminBakeryDetail; onSaved: (d: AdminBakeryDetail) => void }) {
  const [status, setStatus] = useState<BakeryStatus>(detail.summary.status)
  const [instagram, setInstagram] = useState(detail.summary.instagramUsername ?? '')
  const [phone, setPhone] = useState(detail.phone ?? '')
  const [hours, setHours] = useState<HoursState>(() => toHoursState(detail.openingHours))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  async function save(event: React.FormEvent) {
    event.preventDefault()
    const body: Record<string, unknown> = {}
    if (status !== detail.summary.status) body.status = status
    if (instagram.trim() !== (detail.summary.instagramUsername ?? '')) body.instagramUsername = instagram.trim() || null
    if (phone.trim() !== (detail.phone ?? '')) body.phone = phone.trim() || null
    const payload = toHoursPayload(hours)
    if (JSON.stringify(payload) !== JSON.stringify(detail.openingHours ?? null)) body.openingHours = payload
    if (Object.keys(body).length === 0) {
      setError('바뀐 내용이 없어요.')
      return
    }
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      const updated = await updateAdminBakery(detail.summary.id, body)
      setSaved(true)
      onSaved(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  const s = detail.summary
  return (
    <form className="admin-editor" onSubmit={save} aria-label={`${s.name} 수정`}>
      <h3>{s.name}</h3>
      <p className="card__meta">
        {s.districtName} · {detail.jibunAddress ?? s.roadAddress ?? '주소 없음'} · 번호 {s.id}
      </p>
      <p className="detail__links">
        {detail.kakaoMapUrl && (
          <a className="btn btn--small" href={detail.kakaoMapUrl} target="_blank" rel="noopener noreferrer">
            카카오맵
          </a>
        )}
        {detail.naverMapUrl && (
          <a className="btn btn--small" href={detail.naverMapUrl} target="_blank" rel="noopener noreferrer">
            네이버 지도
          </a>
        )}
      </p>
      <div className="admin-row">
        <label className="admin-field">
          상태
          <select value={status} onChange={(e) => setStatus(e.target.value as BakeryStatus)}>
            {(Object.keys(BAKERY_STATUS_LABEL) as BakeryStatus[]).map((value) => (
              <option key={value} value={value}>
                {BAKERY_STATUS_LABEL[value]}
              </option>
            ))}
          </select>
        </label>
        <label className="admin-field">
          인스타그램 계정
          <input value={instagram} maxLength={40} placeholder="@계정 없이 계정명" onChange={(e) => setInstagram(e.target.value)} />
        </label>
        <label className="admin-field">
          전화번호
          <input value={phone} maxLength={30} inputMode="tel" onChange={(e) => setPhone(e.target.value)} />
        </label>
      </div>
      <fieldset className="admin-hours">
        <legend>영업시간 (정하지 않은 요일은 비워 둬요)</legend>
        {DAYS.map(([key, label]) => {
          const day = hours[key]
          const set = (patch: Partial<DayState>) => setHours((h) => ({ ...h, [key]: { ...h[key], ...patch } }))
          return (
            <div key={key} className="admin-hours__row">
              <span>{label}</span>
              <select aria-label={`${label}요일 영업`} value={day.mode} onChange={(e) => set({ mode: e.target.value as DayState['mode'] })}>
                <option value="unset">정보 없음</option>
                <option value="open">영업</option>
                <option value="closed">휴무</option>
              </select>
              {day.mode === 'open' && (
                <>
                  <input type="time" aria-label={`${label}요일 시작`} value={day.from} onChange={(e) => set({ from: e.target.value })} />
                  <span aria-hidden="true">~</span>
                  <input type="time" aria-label={`${label}요일 끝`} value={day.to} onChange={(e) => set({ to: e.target.value })} />
                </>
              )}
            </div>
          )
        })}
      </fieldset>
      <ErrorNote message={error} />
      {saved && (
        <p className="notice" role="status">
          저장했어요.
        </p>
      )}
      <button type="submit" className="btn btn--primary" disabled={busy}>
        {busy ? '저장 중…' : '저장'}
      </button>
    </form>
  )
}

export default function BakeriesPanel() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const status = params.get('status') ?? ''
  const [text, setText] = useState(q)
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const list = useAsync((signal) => searchAdminBakeries(q, status, page, signal), `${q}|${status}|${page}`)
  const detail = useAsync(
    (signal) => (selected === null ? Promise.resolve(null) : fetchAdminBakery(selected, signal)),
    String(selected),
  )

  function update(next: Record<string, string>) {
    setPage(0)
    setParams(Object.fromEntries(Object.entries(next).filter(([, v]) => v !== '')))
  }

  return (
    <section aria-labelledby="bk-title">
      <h2 id="bk-title">빵집 관리</h2>
      <form
        className="admin-row"
        role="search"
        onSubmit={(e) => {
          e.preventDefault()
          update({ q: text.trim(), status })
        }}
      >
        <input
          aria-label="빵집 검색"
          className="admin-grow"
          value={text}
          placeholder="이름·주소·인스타 계정"
          onChange={(e) => setText(e.target.value)}
        />
        <select aria-label="상태" value={status} onChange={(e) => update({ q, status: e.target.value })}>
          <option value="">모든 상태</option>
          {(Object.keys(BAKERY_STATUS_LABEL) as BakeryStatus[]).map((value) => (
            <option key={value} value={value}>
              {BAKERY_STATUS_LABEL[value]}
            </option>
          ))}
        </select>
        <button type="submit" className="btn btn--small">
          검색
        </button>
      </form>
      <ErrorNote message={list.error} />
      {list.data && <p className="summary">{list.data.totalElements}곳</p>}
      <ul className="plain admin-list" aria-busy={list.loading}>
        {list.data?.items.map((b) => (
          <li key={b.id}>
            <button
              type="button"
              className={selected === b.id ? 'admin-row-btn admin-row-btn--on' : 'admin-row-btn'}
              onClick={() => setSelected(b.id)}
            >
              <span>
                <strong>{b.name}</strong> <span className="card__meta">{b.districtName}</span>
              </span>
              <span className="admin-row-btn__meta">
                <Badge tone={b.status === 'ACTIVE' ? 'ok' : b.status === 'PENDING' ? 'plain' : 'danger'}>{BAKERY_STATUS_LABEL[b.status]}</Badge>
                {b.instagramUsername ? <span>@{b.instagramUsername}</span> : <span className="card__meta">인스타 없음</span>}
                {!b.hasOpeningHours && <span className="card__meta">영업시간 없음</span>}
                {b.reviewCount > 0 && <span>★ {formatRating(b.ratingAvg, b.reviewCount)}</span>}
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
            <Editor
              key={detail.data.summary.id}
              detail={detail.data}
              onSaved={() => {
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
