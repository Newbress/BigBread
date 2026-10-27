import { useCallback, useEffect, useMemo, useState } from 'react'
import { addFavorite, fetchFavorites, removeFavorite } from '../api/client.ts'
import type { BakerySummary, FavoriteItem, User } from '../api/types.ts'

type State = { userId: number | null; items: FavoriteItem[]; loading: boolean; error: string | null }

const EMPTY: State = { userId: null, items: [], loading: false, error: null }

/**
 * 내 즐겨찾기. 로그인한 사용자가 바뀌면 다시 불러오고, 담기/빼기는 먼저 화면에 반영한 뒤 서버에 보낸다
 * (실패하면 되돌리고 오류를 보여준다).
 */
export function useFavorites(user: User | null) {
  const userId = user?.id ?? null
  const [state, setState] = useState<State>(EMPTY)

  useEffect(() => {
    if (userId === null) return
    const controller = new AbortController()
    fetchFavorites(controller.signal)
      .then((items) => setState({ userId, items, loading: false, error: null }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setState({ userId, items: [], loading: false, error: e instanceof Error ? e.message : '즐겨찾기를 불러오지 못했어요.' })
      })
    return () => controller.abort()
  }, [userId])

  // 다른 사용자(또는 로그아웃)의 결과는 보이지 않게 한다.
  const current = state.userId === userId && userId !== null ? state : EMPTY
  const loading = userId !== null && state.userId !== userId
  const items = current.items
  const ids = useMemo(() => new Set(items.map((item) => item.bakery.id)), [items])

  const toggle = useCallback(
    async (bakery: BakerySummary) => {
      if (userId === null) return
      const wasFavorite = ids.has(bakery.id)
      const before = items
      const after = wasFavorite
        ? before.filter((item) => item.bakery.id !== bakery.id)
        : [{ bakery, favoritedAt: new Date().toISOString() }, ...before]
      setState({ userId, items: after, loading: false, error: null })
      try {
        await (wasFavorite ? removeFavorite(bakery.id) : addFavorite(bakery.id))
      } catch (e) {
        setState({
          userId,
          items: before,
          loading: false,
          error: e instanceof Error ? e.message : '즐겨찾기를 바꾸지 못했어요.',
        })
      }
    },
    [userId, ids, items],
  )

  return { items, ids, loading, error: current.error, toggle }
}
