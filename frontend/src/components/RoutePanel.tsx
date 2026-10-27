import type { CSSProperties } from 'react'
import { useState } from 'react'
import type { FormEvent } from 'react'
import type { BakerySummary, RouteOrder, RouteResponse } from '../api/types.ts'
import { formatDistance, formatDuration, isHttpUrl } from '../lib/format.ts'
import { MAX_ROUTE_STOPS, ROUTE_ORDER_LABEL, routeColor, SELECTABLE_ROUTE_ORDERS } from '../lib/route.ts'

type Props = {
  basket: BakerySummary[]
  order: RouteOrder
  onOrderChange: (order: RouteOrder) => void
  hasStart: boolean
  route: RouteResponse | null
  planning: boolean
  error: string | null
  onPlan: () => void
  onRemove: (id: number) => void
  onClear: () => void
  onSelect: (id: number) => void
  loggedIn: boolean
  /** 비로그인이면 로그인 창을 연다 */
  onLogin: () => void
  /** 지금 만든 동선(방문 순서 그대로)을 코스로 저장한다. 실패하면 오류를 던진다. */
  onSaveCourse: (title: string, route: RouteResponse) => Promise<void>
  onOpenCourses: () => void
}

function Basket({ basket, onRemove }: Pick<Props, 'basket' | 'onRemove'>) {
  return (
    <ul className="plain basket">
      {basket.map((bakery) => (
        <li key={bakery.id} className="basket__item">
          <span>
            {bakery.name}
            <span className="card__meta"> · {bakery.district.name}</span>
          </span>
          <button type="button" className="btn btn--small" onClick={() => onRemove(bakery.id)}>
            빼기
          </button>
        </li>
      ))}
    </ul>
  )
}

/** 지금 만든 동선을 코스로 저장하는 작은 폼. */
function SaveCourse({
  route,
  loggedIn,
  onLogin,
  onSaveCourse,
  onOpenCourses,
}: Pick<Props, 'loggedIn' | 'onLogin' | 'onSaveCourse' | 'onOpenCourses'> & { route: RouteResponse }) {
  const first = route.stops[0]?.bakery.name ?? '내 코스'
  const defaultTitle = route.stops.length > 1 ? `${first} 외 ${route.stops.length - 1}곳` : first
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState(defaultTitle)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [savedTitle, setSavedTitle] = useState<string | null>(null)

  if (!loggedIn) {
    return (
      <button type="button" className="btn btn--block" onClick={onLogin}>
        로그인하고 코스로 저장
      </button>
    )
  }

  if (savedTitle !== null) {
    return (
      <p className="notice" role="status">
        &lsquo;{savedTitle}&rsquo;를 저장했어요.{' '}
        <button type="button" className="link-btn" onClick={onOpenCourses}>
          내 코스 보기
        </button>
      </p>
    )
  }

  if (!open) {
    return (
      <button type="button" className="btn btn--block" onClick={() => setOpen(true)}>
        코스로 저장
      </button>
    )
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    const trimmed = title.trim()
    if (!trimmed) {
      setError('코스 이름을 입력해 주세요.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await onSaveCourse(trimmed, route)
      setSavedTitle(trimmed)
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장에 실패했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={submit} noValidate>
      <label className="field-block">
        <span className="field-block__label">코스 이름</span>
        <input value={title} maxLength={100} onChange={(event) => setTitle(event.target.value)} />
      </label>
      <p className="card__meta">지금 보이는 방문 순서 그대로 저장돼요. (출발 위치는 저장하지 않아요)</p>
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}
      <div className="course-card__actions">
        <button type="submit" className="btn btn--on" disabled={busy}>
          {busy ? '저장하는 중…' : '저장'}
        </button>
        <button type="button" className="btn" onClick={() => setOpen(false)}>
          취소
        </button>
      </div>
    </form>
  )
}

function Result({ route, onSelect }: Pick<Props, 'route' | 'onSelect'>) {
  if (!route) return null
  return (
    <section aria-label="동선 결과">
      <p className="route__total">
        총 <strong>{formatDistance(route.totalDistanceMeters)}</strong> ·{' '}
        <strong>{formatDuration(route.totalDurationMinutes)}</strong>
        {route.estimated && <span className="badge badge--warn">일부 직선 기준</span>}
      </p>
      {route.estimated && (
        <p className="notice">일부 구간은 실제 길을 찾지 못해 직선거리로 추정한 값이에요.</p>
      )}
      <p className="card__meta">
        도보 시간은 카카오맵 도보 경로 기준이고, 빵집에서 머무는 시간은 포함되지 않아요.
        {route.start && ' 출발은 내 위치예요.'}
      </p>
      <ol className="route">
        {route.stops.map((stop) => (
          <li key={stop.bakery.id} className="route__stop" style={{ '--route-color': routeColor(stop.seq) } as CSSProperties}>
            {stop.leg && stop.leg.distanceMeters === 0 && <p className="route__leg">출발 위치와 같은 곳이에요</p>}
            {stop.leg && stop.leg.distanceMeters > 0 && (
              <p className="route__leg">
                ↓ {stop.leg.estimated && '약 '}{formatDistance(stop.leg.distanceMeters)} · 도보 {formatDuration(stop.leg.durationMinutes)}
                {isHttpUrl(stop.leg.kakaoWalkUrl) && (
                  <>
                    {' '}
                    <a href={stop.leg.kakaoWalkUrl} target="_blank" rel="noopener noreferrer">
                      카카오맵 길찾기
                    </a>
                  </>
                )}
              </p>
            )}
            <button type="button" className="card" onClick={() => onSelect(stop.bakery.id)}>
              <span className="card__rank" aria-hidden="true">
                {stop.seq}
              </span>
              <span className="card__body">
                <span className="card__name">{stop.bakery.name}</span>
                <span className="card__meta">
                  {stop.bakery.district.name}
                  {stop.bakery.roadAddress ? ` · ${stop.bakery.roadAddress}` : ''}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ol>
    </section>
  )
}

export default function RoutePanel(props: Props) {
  const { basket, order, onOrderChange, hasStart, route, planning, error, onPlan, onClear } = props

  return (
    <section className="detail" aria-label="내 동선" aria-busy={planning}>
      <h2>
        내 동선{' '}
        <span className="card__meta">
          {basket.length}/{MAX_ROUTE_STOPS}곳
        </span>
      </h2>

      {basket.length === 0 ? (
        <p className="notice">목록에서 &lsquo;담기&rsquo;를 눌러 가고 싶은 빵집을 담아 보세요.</p>
      ) : (
        <>
          <Basket basket={basket} onRemove={props.onRemove} />

          <div className="toolbar">
            <label className="field">
              방문 순서
              <select value={order} onChange={(e) => onOrderChange(e.target.value as RouteOrder)}>
                {SELECTABLE_ROUTE_ORDERS.map((value) => (
                  <option key={value} value={value}>
                    {ROUTE_ORDER_LABEL[value]}
                  </option>
                ))}
              </select>
            </label>
            <button type="button" className="btn btn--small" onClick={onClear}>
              모두 비우기
            </button>
          </div>

          <p className="card__meta">
            {hasStart ? '출발: 내 위치' : '내 위치를 켜면 내 위치에서 출발하는 동선을 만들어요. (지금은 첫 빵집부터)'}
          </p>

          <button type="button" className="btn btn--on" onClick={onPlan} disabled={planning}>
            {planning ? '동선 만드는 중…' : route ? '다시 만들기' : '동선 만들기'}
          </button>

          {error && (
            <p className="notice notice--error" role="alert">
              {error}
            </p>
          )}
          <Result route={route} onSelect={props.onSelect} />
          {route && (
            <SaveCourse
              key={route.stops.map((stop) => stop.bakery.id).join(',')}
              route={route}
              loggedIn={props.loggedIn}
              onLogin={props.onLogin}
              onSaveCourse={props.onSaveCourse}
              onOpenCourses={props.onOpenCourses}
            />
          )}
        </>
      )}
    </section>
  )
}
