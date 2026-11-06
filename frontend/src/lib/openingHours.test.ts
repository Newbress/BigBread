import { describe, expect, it } from 'vitest'
import { toHoursPayload, toHoursState } from './openingHours.ts'

describe('영업시간 변환', () => {
  it('서버 값을 편집 상태로 바꿨다가 그대로 되돌린다', () => {
    const hours = { mon: 'closed', tue: '08:00-21:00', sat: '10:00-02:00' }
    const state = toHoursState(hours)
    expect(state.mon.mode).toBe('closed')
    expect(state.tue).toEqual({ mode: 'open', from: '08:00', to: '21:00' })
    expect(state.wed.mode).toBe('unset')
    expect(toHoursPayload(state)).toEqual(hours)
  })

  it('아무 요일도 정하지 않으면 null이다 (영업시간 지우기)', () => {
    expect(toHoursPayload(toHoursState(null))).toBeNull()
    expect(toHoursPayload(toHoursState({}))).toBeNull()
  })

  it('편집한 시간이 반영된다', () => {
    const state = toHoursState({ mon: '09:00-18:00' })
    state.mon = { ...state.mon, to: '20:30' }
    expect(toHoursPayload(state)).toEqual({ mon: '09:00-20:30' })
  })
})
