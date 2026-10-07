import { useEffect, useRef } from 'react'
import type { BakerySummary } from '../api/types.ts'
import { formatDistance } from '../lib/format.ts'

type Props = {
  items: BakerySummary[]
  total: number
  loading: boolean
  error: string | null
  hasLocation: boolean
  selectedId: number | null
  onSelect: (id: number) => void
}

type ItemProps = {
  bakery: BakerySummary
  rank: number
  selected: boolean
  onSelect: (id: number) => void
}

function Item({ bakery, rank, selected, onSelect }: ItemProps) {
  const ref = useRef<HTMLLIElement>(null)

  useEffect(() => {
    if (selected) ref.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [selected])

  return (
    <li ref={ref}>
      <button
        type="button"
        className={selected ? 'card card--selected' : 'card'}
        aria-current={selected ? 'true' : undefined}
        onClick={() => onSelect(bakery.id)}
      >
        <span className="card__rank" aria-hidden="true">
          {rank}
        </span>
        <span className="card__body">
          <span className="card__title">
            <span className="card__name">{bakery.name}</span>
            {bakery.distanceMeters != null && (
              <span className="badge">{formatDistance(bakery.distanceMeters)}</span>
            )}
          </span>
          <span className="card__meta">
            {bakery.district.name}
            {bakery.roadAddress ? ` · ${bakery.roadAddress}` : ''}
          </span>
          {bakery.signatureBreads.length > 0 && (
            <span className="card__breads">
              {bakery.signatureBreads.map((bread) => (
                <span key={bread} className="tag">
                  {bread}
                </span>
              ))}
            </span>
          )}
        </span>
      </button>
    </li>
  )
}

export default function BakeryList({ items, total, loading, error, hasLocation, selectedId, onSelect }: Props) {
  return (
    <section aria-label="빵집 목록" aria-busy={loading}>
      <p className="summary" aria-live="polite">
        {loading ? '불러오는 중…' : `${total}곳`}
        {!loading && total > items.length && ` (상위 ${items.length}곳만 표시)`}
      </p>

      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}

      {!loading && !error && items.length === 0 && (
        <p className="notice">
          조건에 맞는 빵집이 없어요.
          {hasLocation && ' 반경을 넓히거나 내 위치를 꺼 보세요.'}
        </p>
      )}

      <ol className="list">
        {items.map((bakery, index) => (
          <Item
            key={bakery.id}
            bakery={bakery}
            rank={index + 1}
            selected={bakery.id === selectedId}
            onSelect={onSelect}
          />
        ))}
      </ol>
    </section>
  )
}
