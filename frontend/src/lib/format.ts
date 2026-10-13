/** 거리를 사람이 읽기 좋게: 320m, 1.2km */
export function formatDistance(meters: number | null | undefined): string {
  if (meters == null) return ''
  if (meters < 1000) return `${Math.round(meters)}m`
  return `${(meters / 1000).toFixed(1)}km`
}

/** 3500 -> 3,500원 */
export function formatPrice(price: number | null | undefined): string {
  if (price == null) return ''
  return `${price.toLocaleString('ko-KR')}원`
}

/** 외부 링크는 http(s)만 허용한다 (수집된 URL이 javascript: 등일 수 없게). */
export function isHttpUrl(value: string | null | undefined): value is string {
  if (!value) return false
  try {
    const { protocol } = new URL(value)
    return protocol === 'http:' || protocol === 'https:'
  } catch {
    return false
  }
}

const DAY_KEYS = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'] as const
const DAY_LABELS: Record<(typeof DAY_KEYS)[number], string> = {
  sun: '일',
  mon: '월',
  tue: '화',
  wed: '수',
  thu: '목',
  fri: '금',
  sat: '토',
}

export type OpeningHoursRow = { day: string; text: string; today: boolean }

/**
 * 영업시간 JSON을 월~일 순서 행으로 바꾼다. 형식이 맞지 않으면 null.
 * 값이 "closed"이면 "휴무"로 표시한다. today는 `now` 기준 요일.
 */
export function toOpeningHoursRows(hours: unknown, now: Date = new Date()): OpeningHoursRow[] | null {
  if (typeof hours !== 'object' || hours === null) return null
  const record = hours as Record<string, unknown>
  const todayKey = DAY_KEYS[now.getDay()]

  const rows = (['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'] as const)
    .filter((key) => typeof record[key] === 'string')
    .map((key) => {
      const raw = record[key] as string
      return {
        day: DAY_LABELS[key],
        text: raw.toLowerCase() === 'closed' ? '휴무' : raw,
        today: key === todayKey,
      }
    })
  return rows.length > 0 ? rows : null
}

export const PLATFORM_LABEL = {
  NAVER_BLOG: '네이버 블로그',
  INSTAGRAM: '인스타그램',
} as const
