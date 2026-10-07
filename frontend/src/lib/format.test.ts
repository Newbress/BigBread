import { describe, expect, it } from 'vitest'
import { formatDistance, formatPrice, isHttpUrl, toOpeningHoursRows } from './format.ts'

describe('formatDistance', () => {
  it('1km 미만은 미터로 반올림해서 보여준다', () => {
    expect(formatDistance(0)).toBe('0m')
    expect(formatDistance(320.4)).toBe('320m')
    expect(formatDistance(999.4)).toBe('999m')
  })

  it('1km 이상은 소수 한 자리 km로 보여준다', () => {
    expect(formatDistance(1000)).toBe('1.0km')
    expect(formatDistance(1234)).toBe('1.2km')
  })

  it('값이 없으면 빈 문자열', () => {
    expect(formatDistance(null)).toBe('')
    expect(formatDistance(undefined)).toBe('')
  })
})

describe('formatPrice', () => {
  it('천 단위 구분과 원을 붙인다', () => {
    expect(formatPrice(3500)).toBe('3,500원')
    expect(formatPrice(12000)).toBe('12,000원')
  })

  it('값이 없으면 빈 문자열', () => {
    expect(formatPrice(null)).toBe('')
  })
})

describe('isHttpUrl', () => {
  it('http, https만 허용한다', () => {
    expect(isHttpUrl('https://example.com/a')).toBe(true)
    expect(isHttpUrl('http://example.com')).toBe(true)
  })

  it('javascript:, data: 등 다른 스킴과 잘못된 값은 거부한다', () => {
    expect(isHttpUrl('javascript:alert(1)')).toBe(false)
    expect(isHttpUrl('data:text/html,<script>1</script>')).toBe(false)
    expect(isHttpUrl('not a url')).toBe(false)
    expect(isHttpUrl('')).toBe(false)
    expect(isHttpUrl(null)).toBe(false)
  })
})

describe('toOpeningHoursRows', () => {
  const hours = {
    mon: 'closed',
    tue: '10:00-19:00',
    wed: '10:00-19:00',
    thu: '10:00-19:00',
    fri: '10:00-19:00',
    sat: '10:00-19:00',
    sun: '10:00-17:00',
  }
  // 2026-10-07은 수요일
  const wednesday = new Date(2026, 9, 7)

  it('월요일부터 일요일 순서로 만들고 closed는 휴무로 바꾼다', () => {
    const rows = toOpeningHoursRows(hours, wednesday)
    expect(rows?.map((row) => row.day)).toEqual(['월', '화', '수', '목', '금', '토', '일'])
    expect(rows?.[0]).toMatchObject({ day: '월', text: '휴무' })
    expect(rows?.[6]).toMatchObject({ day: '일', text: '10:00-17:00' })
  })

  it('오늘 요일만 today로 표시한다', () => {
    const rows = toOpeningHoursRows(hours, wednesday) ?? []
    expect(rows.filter((row) => row.today).map((row) => row.day)).toEqual(['수'])
  })

  it('일부 요일만 있어도 있는 것만 보여준다', () => {
    expect(toOpeningHoursRows({ sat: '09:00-12:00' }, wednesday)).toEqual([
      { day: '토', text: '09:00-12:00', today: false },
    ])
  })

  it('형식이 맞지 않으면 null', () => {
    expect(toOpeningHoursRows(null)).toBeNull()
    expect(toOpeningHoursRows('08:00-20:00')).toBeNull()
    expect(toOpeningHoursRows({})).toBeNull()
    expect(toOpeningHoursRows({ mon: 123 })).toBeNull()
  })
})
