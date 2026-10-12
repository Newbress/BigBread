import type { SearchParams } from '../api/types.ts'

/** 목록 API의 최대 페이지 크기 (백엔드 검증과 같다). */
export const PAGE_SIZE = 50

/**
 * 검색 조건을 `/api/bakeries` 쿼리 문자열로 만든다.
 * 위치가 없으면 거리 관련 파라미터를 보내지 않고, 거리순은 인기순으로 되돌린다 (백엔드가 400을 주기 때문).
 */
export function buildBakeryQuery(params: SearchParams): string {
  const query = new URLSearchParams()
  const { district, location, radius, sort, size = PAGE_SIZE } = params

  if (district) query.set('district', district)
  if (location) {
    query.set('lat', String(location.lat))
    query.set('lng', String(location.lng))
    if (radius) query.set('radius', String(radius))
  }
  query.set('sort', sort === 'DISTANCE' && !location ? 'POPULARITY' : sort)
  query.set('size', String(size))
  return query.toString()
}
