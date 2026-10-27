import type { BakerySummary, FavoriteItem } from '../api/types.ts'
import { BakeryListItem } from './BakeryList.tsx'

type Props = {
  loggedIn: boolean
  onLogin: () => void
  items: FavoriteItem[]
  loading: boolean
  error: string | null
  selectedId: number | null
  onSelect: (id: number) => void
  basketIds: ReadonlySet<number>
  basketFull: boolean
  onToggleBasket: (bakery: BakerySummary) => void
  favoriteIds: ReadonlySet<number>
  onToggleFavorite: (bakery: BakerySummary) => void
}

/** 내가 즐겨찾기한 빵집. 최근에 담은 순서이고, 지도에도 이 빵집들이 표시된다. */
export default function FavoritesPanel({
  loggedIn,
  onLogin,
  items,
  loading,
  error,
  selectedId,
  onSelect,
  basketIds,
  basketFull,
  onToggleBasket,
  favoriteIds,
  onToggleFavorite,
}: Props) {
  if (!loggedIn) {
    return (
      <section aria-label="즐겨찾기">
        <p className="notice">로그인하면 마음에 드는 빵집을 즐겨찾기에 모아 둘 수 있어요.</p>
        <button type="button" className="btn btn--on btn--block" onClick={onLogin}>
          로그인
        </button>
      </section>
    )
  }

  return (
    <section aria-label="즐겨찾기" aria-busy={loading}>
      <p className="summary" aria-live="polite">
        {loading ? '불러오는 중…' : `${items.length}곳`}
      </p>
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}
      {!loading && !error && items.length === 0 && (
        <p className="notice">아직 즐겨찾기한 빵집이 없어요. 목록이나 상세에서 하트(♡)를 눌러 보세요.</p>
      )}
      <ol className="list">
        {items.map((item, index) => (
          <BakeryListItem
            key={item.bakery.id}
            bakery={item.bakery}
            rank={index + 1}
            selected={item.bakery.id === selectedId}
            onSelect={onSelect}
            inBasket={basketIds.has(item.bakery.id)}
            basketFull={basketFull}
            onToggleBasket={onToggleBasket}
            isFavorite={favoriteIds.has(item.bakery.id)}
            onToggleFavorite={onToggleFavorite}
          />
        ))}
      </ol>
    </section>
  )
}
