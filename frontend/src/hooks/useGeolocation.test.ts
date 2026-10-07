import { describe, expect, it } from 'vitest'
import { isInKorea } from './useGeolocation.ts'

describe('isInKorea', () => {
  it('대전 좌표는 한국 안이다', () => {
    expect(isInKorea({ lat: 36.3504, lng: 127.3845 })).toBe(true)
  })

  it('경계 값은 포함한다', () => {
    expect(isInKorea({ lat: 33, lng: 124 })).toBe(true)
    expect(isInKorea({ lat: 39, lng: 132 })).toBe(true)
  })

  it('한국 밖 좌표는 거부한다', () => {
    expect(isInKorea({ lat: 37.7749, lng: -122.4194 })).toBe(false) // 샌프란시스코
    expect(isInKorea({ lat: 35.6762, lng: 139.6503 })).toBe(false) // 도쿄
    expect(isInKorea({ lat: 0, lng: 0 })).toBe(false)
  })
})
