import type { Coords, RouteOrder } from '../api/types.ts'

/** 동선 하나에 담을 수 있는 최대 빵집 수 (백엔드 제한과 같다). */
export const MAX_ROUTE_STOPS = 10

export const ROUTE_ORDER_LABEL: Record<RouteOrder, string> = {
  NEAREST: '최단 동선',
  POPULARITY: '인기순',
  MANUAL: '담은 순서',
}

/** 화면에서 고를 수 있는 방문 순서 (인기순은 API만 지원한다). */
export const SELECTABLE_ROUTE_ORDERS: RouteOrder[] = ['NEAREST', 'MANUAL']

/**
 * 정류장 번호별 색. N번 정류장 마커와, 그곳으로 가는 구간(이전 지점 → N번)의 선이 같은 색이다.
 * 1번은 갈색, 2번은 초록처럼 번호만 봐도 선이 어디로 이어지는지 알 수 있다.
 */
export const ROUTE_COLORS = [
  '#b45309', // 1 갈색
  '#15803d', // 2 초록
  '#1d4ed8', // 3 파랑
  '#be185d', // 4 자주
  '#7e22ce', // 5 보라
  '#0e7490', // 6 청록
  '#dc2626', // 7 빨강
  '#334155', // 8 남회색
  '#65a30d', // 9 연두
  '#ea580c', // 10 주황
] as const

export function routeColor(seq: number): string {
  return ROUTE_COLORS[(seq - 1 + ROUTE_COLORS.length) % ROUTE_COLORS.length]
}

export type RouteSegment = { path: Coords[]; color: string; estimated: boolean }

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
