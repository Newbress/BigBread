import { describe, expect, it } from 'vitest'
import { checkPhotoCandidate, fitSize } from './photo.ts'

describe('fitSize', () => {
  it('긴 변이 한도보다 크면 비율을 유지해서 줄인다', () => {
    expect(fitSize(4000, 3000)).toEqual({ width: 2560, height: 1920 })
    expect(fitSize(3000, 4000)).toEqual({ width: 1920, height: 2560 })
    expect(fitSize(8064, 6048)).toEqual({ width: 2560, height: 1920 })
  })

  it('한도 이하이면 그대로 둔다 (키우지 않는다)', () => {
    expect(fitSize(1600, 1200)).toEqual({ width: 1600, height: 1200 })
    expect(fitSize(2560, 1000)).toEqual({ width: 2560, height: 1000 })
  })

  it('아주 긴 사진도 한 변이 0이 되지 않는다', () => {
    expect(fitSize(100000, 10).height).toBeGreaterThanOrEqual(1)
  })
})

describe('checkPhotoCandidate', () => {
  it('빈 파일은 거부한다', () => {
    expect(checkPhotoCandidate(new File([], 'a.jpg'))).not.toBeNull()
    expect(checkPhotoCandidate(new File(['x'], 'a.jpg'))).toBeNull()
  })
})
