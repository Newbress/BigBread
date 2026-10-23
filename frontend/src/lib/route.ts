import type { Coords, RouteOrder } from '../api/types.ts'

/** 동선 하나에 담을 수 있는 최대 빵집 수 (백엔드 제한과 같다). */
export const MAX_ROUTE_STOPS = 10

export const ROUTE_ORDER_LABEL: Record<RouteOrder, string> = {
  NEAREST: '최단 동선',
  POPULARITY: '인기순',
  MANUAL: '담은 순서',
}

/** 같은 입력이면 같은 키: 담은 빵집·방법·출발지가 바뀌면 이전 결과는 낡은 것으로 본다. */
export function routeKey(ids: number[], order: RouteOrder, start: Coords | null): string {
  const from = start ? `${start.lat.toFixed(5)},${start.lng.toFixed(5)}` : '-'
  return `${order}|${from}|${ids.join(',')}`
}

/** 목록에 항목을 넣거나(없을 때) 뺀다(있을 때). 가득 찼으면 넣지 않는다. */
export function toggleById<T extends { id: number }>(items: T[], item: T, max: number = MAX_ROUTE_STOPS): T[] {
  if (items.some((existing) => existing.id === item.id)) return items.filter((existing) => existing.id !== item.id)
  return items.length >= max ? items : [...items, item]
}
