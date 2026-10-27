import { useEffect, useRef } from 'react'
import type { BakerySummary } from '../api/types.ts'
import { formatDistance } from '../lib/format.ts'
import FavoriteButton from './FavoriteButton.tsx'

type Props = {
  items: BakerySummary[]
  total: number
  loading: boolean
  error: string | null
  hasLocation: boolean
  selectedId: number | null
  onSelect: (id: number) => void
  basketIds: ReadonlySet<number>
  basketFull: boolean
  onToggleBasket: (bakery: BakerySummary) => void
  favoriteIds: ReadonlySet<number>
  onToggleFavorite: (bakery: BakerySummary) => void
}

type ItemProps = {
  bakery: BakerySummary
  rank: number
  selected: boolean
  onSelect: (id: number) => void
  inBasket: boolean
  basketFull: boolean
  onToggleBasket: (bakery: BakerySummary) => void
  isFavorite: boolean
  onToggleFavorite: (bakery: BakerySummary) => void
}

/** 빵집 한 줄: 카드(눌러서 상세) + 동선에 담기 + 즐겨찾기. 즐겨찾기 탭에서도 같이 쓴다. */
export function BakeryListItem({
  bakery,
  rank,
  selected,
  onSelect,
  inBasket,
  basketFull,
  onToggleBasket,
  isFavorite,
  onToggleFavorite,
}: ItemProps) {
  const ref = useRef<HTMLLIElement>(null)

  useEffect(() => {
    if (selected) ref.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [selected])

  return (
    <li ref={ref} className="list__row">
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
      <div className="list__actions">
        <button
          type="button"
          className={inBasket ? 'btn btn--small btn--toggle btn--on' : 'btn btn--small btn--toggle'}
          disabled={!inBasket && basketFull}
          title={!inBasket && basketFull ? '동선에는 최대 10곳까지 담을 수 있어요' : undefined}
          aria-label={`${inBasket ? '담음' : '담기'}: ${bakery.name}`}
          onClick={() => onToggleBasket(bakery)}
        >
          {inBasket ? '담음' : '담기'}
        </button>
        <FavoriteButton active={isFavorite} name={bakery.name} onToggle={() => onToggleFavorite(bakery)} />
      </div>
    </li>
  )
}

export default function BakeryList({
  items,
  total,
  loading,
  error,
  hasLocation,
  selectedId,
  onSelect,
  basketIds,
  basketFull,
  onToggleBasket,
  favoriteIds,
  onToggleFavorite,
}: Props) {
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
          <BakeryListItem
            key={bakery.id}
            bakery={bakery}
            rank={index + 1}
            selected={bakery.id === selectedId}
            onSelect={onSelect}
            inBasket={basketIds.has(bakery.id)}
            basketFull={basketFull}
            onToggleBasket={onToggleBasket}
            isFavorite={favoriteIds.has(bakery.id)}
            onToggleFavorite={onToggleFavorite}
          />
        ))}
      </ol>
    </section>
  )
}
