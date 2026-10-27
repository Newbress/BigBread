import { useCallback, useMemo, useState } from 'react'
import { planRoute } from '../api/client.ts'
import type { BakerySummary, Coords, RouteOrder, RouteResponse } from '../api/types.ts'
import { MAX_ROUTE_STOPS, routeKey, toggleById } from '../lib/route.ts'

/** 어떤 입력(key)에 대한 결과인지 함께 저장해서, 입력이 바뀌면 낡은 결과를 숨긴다. */
type Planned = { key: string; route: RouteResponse | null; error: string | null }

/**
 * 동선에 담은 빵집(basket)과 동선 계산 결과를 관리한다.
 * 담은 빵집은 목록 필터가 바뀌어도 남도록 요약 정보 전체를 들고 있는다.
 */
export function useRoute(start: Coords | null) {
  const [basket, setBasket] = useState<BakerySummary[]>([])
  const [order, setOrder] = useState<RouteOrder>('NEAREST')
  const [planned, setPlanned] = useState<Planned | null>(null)
  const [pendingKey, setPendingKey] = useState<string | null>(null)

  const ids = useMemo(() => basket.map((bakery) => bakery.id), [basket])
  const key = routeKey(ids, order, start)

  const toggle = useCallback((bakery: BakerySummary) => setBasket((current) => toggleById(current, bakery)), [])

  const remove = useCallback((id: number) => setBasket((current) => current.filter((item) => item.id !== id)), [])
  const clear = useCallback(() => setBasket([]), [])

  /** 저장한 코스를 불러온다: 담은 빵집을 통째로 바꾸고, 저장된 순서를 그대로 쓰도록 '담은 순서'로 둔다. */
  const replaceBasket = useCallback((stops: BakerySummary[]) => {
    setBasket(stops.slice(0, MAX_ROUTE_STOPS))
    setOrder('MANUAL')
  }, [])

  const plan = useCallback(async () => {
    const requestKey = key
    setPendingKey(requestKey)
    try {
      const route = await planRoute({ bakeryIds: ids, start, order })
      setPlanned({ key: requestKey, route, error: null })
    } catch (e) {
      setPlanned({
        key: requestKey,
        route: null,
        error: e instanceof Error ? e.message : '동선을 만들지 못했어요.',
      })
    } finally {
      setPendingKey((current) => (current === requestKey ? null : current))
    }
  }, [ids, key, order, start])

  const fresh = planned !== null && planned.key === key
  return {
    basket,
    order,
    setOrder,
    toggle,
    remove,
    clear,
    replaceBasket,
    plan,
    full: basket.length >= MAX_ROUTE_STOPS,
    planning: pendingKey === key,
    route: fresh ? planned.route : null,
    error: fresh ? planned.error : null,
  }
}
