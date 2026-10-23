import { describe, expect, it } from 'vitest'
import { formatDuration } from './format.ts'
import { MAX_ROUTE_STOPS, ROUTE_COLORS, routeColor, routeKey, toggleById } from './route.ts'

describe('toggleById', () => {
  const item = (id: number) => ({ id })

  it('없으면 뒤에 추가하고, 있으면 뺀다', () => {
    expect(toggleById([item(1), item(2)], item(3))).toEqual([item(1), item(2), item(3)])
    expect(toggleById([item(1), item(2), item(3)], item(2))).toEqual([item(1), item(3)])
  })

  it('가득 차면 더 넣지 않지만 빼는 것은 된다', () => {
    const full = Array.from({ length: MAX_ROUTE_STOPS }, (_, i) => item(i + 1))
    expect(toggleById(full, item(99))).toBe(full)
    expect(toggleById(full, item(1))).toHaveLength(MAX_ROUTE_STOPS - 1)
  })
})

describe('routeKey', () => {
  it('순서·출발지·빵집이 같으면 같은 키다', () => {
    const start = { lat: 36.3283, lng: 127.428 }
    expect(routeKey([1, 2], 'NEAREST', start)).toBe(routeKey([1, 2], 'NEAREST', { ...start }))
  })

  it('무엇이든 바뀌면 키가 달라진다', () => {
    const start = { lat: 36.3283, lng: 127.428 }
    const base = routeKey([1, 2], 'NEAREST', start)
    expect(routeKey([2, 1], 'NEAREST', start)).not.toBe(base)
    expect(routeKey([1, 2], 'MANUAL', start)).not.toBe(base)
    expect(routeKey([1, 2], 'NEAREST', null)).not.toBe(base)
  })
})

describe('formatDuration', () => {
  it('1시간 미만은 분, 이상은 시간과 분으로 보여준다', () => {
    expect(formatDuration(0)).toBe('0분')
    expect(formatDuration(59)).toBe('59분')
    expect(formatDuration(60)).toBe('1시간')
    expect(formatDuration(65)).toBe('1시간 5분')
  })
})

describe('routeColor', () => {
  it('1번은 갈색, 2번은 초록이고 10곳까지 모두 다른 색이다', () => {
    expect(routeColor(1)).toBe('#b45309')
    expect(routeColor(2)).toBe('#15803d')
    const colors = Array.from({ length: MAX_ROUTE_STOPS }, (_, i) => routeColor(i + 1))
    expect(new Set(colors).size).toBe(MAX_ROUTE_STOPS)
  })

  it('색 목록보다 번호가 커지면 처음부터 다시 쓴다', () => {
    expect(routeColor(ROUTE_COLORS.length + 1)).toBe(routeColor(1))
  })
})
