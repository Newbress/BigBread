import { describe, expect, it } from 'vitest'
import { readCookie } from './cookie.ts'

describe('readCookie', () => {
  it('이름이 같은 쿠키 값을 읽는다', () => {
    expect(readCookie('XSRF-TOKEN', 'a=1; XSRF-TOKEN=abc-123; b=2')).toBe('abc-123')
  })

  it('이름이 일부만 같은 쿠키는 무시한다', () => {
    expect(readCookie('TOKEN', 'XSRF-TOKEN=abc')).toBeNull()
  })

  it('없거나 빈 문자열이면 null이다', () => {
    expect(readCookie('x', '')).toBeNull()
    expect(readCookie('x', 'a=1')).toBeNull()
  })

  it('퍼센트 인코딩을 풀고, 깨진 인코딩은 원문 그대로 돌려준다', () => {
    expect(readCookie('n', 'n=%ED%85%8C%EC%8A%A4%ED%8A%B8')).toBe('테스트')
    expect(readCookie('n', 'n=%E0%A4%A')).toBe('%E0%A4%A')
  })

  it('값에 = 가 들어 있어도 첫 = 기준으로 나눈다', () => {
    expect(readCookie('t', 't=a=b==')).toBe('a=b==')
  })
})
