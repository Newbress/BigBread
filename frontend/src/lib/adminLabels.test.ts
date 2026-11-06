import { describe, expect, it } from 'vitest'
import { auditActionLabel } from './adminLabels.ts'
import { formatDateTime } from './format.ts'

describe('auditActionLabel', () => {
  it('알려진 작업은 한글로, 모르는 작업은 원래 이름 그대로 보여준다', () => {
    expect(auditActionLabel('USER_SUSPEND')).toBe('사용자 정지')
    expect(auditActionLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
  })
})

describe('formatDateTime', () => {
  it('비어 있거나 잘못된 값은 빈 문자열이다', () => {
    expect(formatDateTime(null)).toBe('')
    expect(formatDateTime('not a date')).toBe('')
    expect(formatDateTime('2026-11-02T09:05:00Z')).not.toBe('')
  })
})
