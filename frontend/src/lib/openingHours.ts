import type { OpeningHours } from '../api/adminTypes.ts'

export const DAYS = [
  ['mon', '월'],
  ['tue', '화'],
  ['wed', '수'],
  ['thu', '목'],
  ['fri', '금'],
  ['sat', '토'],
  ['sun', '일'],
] as const

export type DayKey = (typeof DAYS)[number][0]
export type DayState = { mode: 'unset' | 'closed' | 'open'; from: string; to: string }
export type HoursState = Record<DayKey, DayState>

/** 서버의 영업시간(요일별 "09:00-21:00" 또는 "closed")을 편집 화면 상태로. */
export function toHoursState(hours: OpeningHours | null): HoursState {
  const state = {} as HoursState
  for (const [key] of DAYS) {
    const raw = hours?.[key]
    if (!raw) state[key] = { mode: 'unset', from: '09:00', to: '21:00' }
    else if (raw === 'closed') state[key] = { mode: 'closed', from: '09:00', to: '21:00' }
    else {
      const [from, to] = raw.split('-')
      state[key] = { mode: 'open', from, to }
    }
  }
  return state
}

/** 편집 화면 상태를 서버가 받는 형식으로. 아무 요일도 정하지 않았으면 null(영업시간 지우기). */
export function toHoursPayload(state: HoursState): OpeningHours | null {
  const result: OpeningHours = {}
  for (const [key] of DAYS) {
    const day = state[key]
    if (day.mode === 'closed') result[key] = 'closed'
    else if (day.mode === 'open') result[key] = `${day.from}-${day.to}`
  }
  return Object.keys(result).length === 0 ? null : result
}
