import { describe, expect, it } from 'vitest'
import { privacy, terms } from './documents.ts'

describe('법적 문서', () => {
  it('약관과 처리방침은 모든 절에 내용이 있다', () => {
    for (const doc of [terms(), privacy()]) {
      expect(doc.sections.length).toBeGreaterThan(5)
      for (const s of doc.sections) {
        expect((s.paragraphs?.length ?? 0) + (s.items?.length ?? 0)).toBeGreaterThan(0)
      }
    }
  })

  it('운영자 정보가 비어 있으면 미설정이라고 표시한다', () => {
    expect(JSON.stringify(privacy())).toContain('미설정')
  })
})
