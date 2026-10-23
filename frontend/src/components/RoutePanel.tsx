import type { BakerySummary, RouteOrder, RouteResponse } from '../api/types.ts'
import { formatDistance, formatDuration, isHttpUrl } from '../lib/format.ts'
import { MAX_ROUTE_STOPS, ROUTE_ORDER_LABEL } from '../lib/route.ts'

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
  onBack: () => void
}

const ORDERS: RouteOrder[] = ['NEAREST', 'POPULARITY', 'MANUAL']

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
      <ol className="route">
        {route.stops.map((stop) => (
          <li key={stop.bakery.id} className="route__stop">
            {stop.leg && stop.leg.distanceMeters === 0 && <p className="route__leg">출발 위치와 같은 곳이에요</p>}
            {stop.leg && stop.leg.distanceMeters > 0 && (
              <p className="route__leg">
                ↓ {formatDistance(stop.leg.distanceMeters)} · 도보 {formatDuration(stop.leg.durationMinutes)}
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
  const { basket, order, onOrderChange, hasStart, route, planning, error, onPlan, onClear, onBack } = props

  return (
    <section className="detail" aria-label="내 동선" aria-busy={planning}>
      <button type="button" className="btn btn--ghost" onClick={onBack}>
        ← 목록으로
      </button>
      <h2>
        내 동선{' '}
        <span className="card__meta">
          {basket.length}/{MAX_ROUTE_STOPS}곳
        </span>
      </h2>

      {basket.length === 0 ? (
        <p className="notice">목록에서 &lsquo;동선에 담기&rsquo;를 눌러 가고 싶은 빵집을 담아 보세요.</p>
      ) : (
        <>
          <Basket basket={basket} onRemove={props.onRemove} />

          <div className="toolbar">
            <label className="field">
              방문 순서
              <select value={order} onChange={(e) => onOrderChange(e.target.value as RouteOrder)}>
                {ORDERS.map((value) => (
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
        </>
      )}
    </section>
  )
}
