import { describe, expect, it } from 'vitest'
import type { SearchParams } from '../api/types.ts'
import { buildBakeryQuery, PAGE_SIZE } from './query.ts'

const base: SearchParams = { district: null, location: null, radius: null, sort: 'POPULARITY' }

function parse(params: SearchParams) {
  return new URLSearchParams(buildBakeryQuery(params))
}

describe('buildBakeryQuery', () => {
  it('아무 조건이 없으면 인기순과 기본 크기만 보낸다', () => {
    const query = parse(base)
    expect([...query.keys()].sort()).toEqual(['size', 'sort'])
    expect(query.get('sort')).toBe('POPULARITY')
    expect(query.get('size')).toBe(String(PAGE_SIZE))
  })

  it('구 코드를 그대로 보낸다', () => {
    expect(parse({ ...base, district: 'YUSEONG' }).get('district')).toBe('YUSEONG')
  })

  it('위치와 반경을 함께 보낸다', () => {
    const query = parse({ ...base, location: { lat: 36.3283, lng: 127.428 }, radius: 1500, sort: 'DISTANCE' })
    expect(query.get('lat')).toBe('36.3283')
    expect(query.get('lng')).toBe('127.428')
    expect(query.get('radius')).toBe('1500')
    expect(query.get('sort')).toBe('DISTANCE')
  })

  it('반경이 null이면 radius를 보내지 않는다 (제한 없음)', () => {
    const query = parse({ ...base, location: { lat: 36.3, lng: 127.4 }, radius: null })
    expect(query.has('radius')).toBe(false)
  })

  it('위치가 없으면 radius를 무시한다', () => {
    const query = parse({ ...base, radius: 2000 })
    expect(query.has('radius')).toBe(false)
    expect(query.has('lat')).toBe(false)
  })

  it('위치가 없는데 거리순이면 인기순으로 되돌린다 (백엔드는 400을 준다)', () => {
    expect(parse({ ...base, sort: 'DISTANCE' }).get('sort')).toBe('POPULARITY')
  })

  it('size를 지정할 수 있다', () => {
    expect(parse({ ...base, size: 10 }).get('size')).toBe('10')
  })
})
