/** 서버와 맞춘 한도. 서버가 다시 한 번 검사한다. */
export const MAX_PHOTOS = 3
export const MAX_PHOTO_BYTES = 10 * 1024 * 1024
export const MAX_PHOTO_EDGE = 2560

const KEEP_AS_IS = new Set(['image/jpeg', 'image/png', 'image/webp'])

/** 긴 변이 max를 넘으면 비율을 유지해서 max에 맞춘다. 작으면 그대로. */
export function fitSize(width: number, height: number, max = MAX_PHOTO_EDGE): { width: number; height: number } {
  const longEdge = Math.max(width, height)
  if (longEdge <= max) return { width, height }
  const scale = max / longEdge
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) }
}

/** 올리기 전에 한 번 확인한다. 문제가 있으면 사용자에게 보여줄 문구를 돌려준다. */
export function checkPhotoCandidate(file: File): string | null {
  if (file.size === 0) return '빈 파일이에요.'
  return null
}

function toBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
}

/**
 * 올릴 사진을 준비한다.
 * - 이미 작은 JPEG/PNG/WebP는 원본 그대로 보낸다 (화질 손실 없음).
 * - 큰 사진과 HEIC(아이폰)는 브라우저가 읽을 수 있으면 긴 변 2560px 이하 JPEG(품질 90)로 줄여서 보낸다.
 *   그러면 폰 원본 몇 MB짜리도 1~2MB로 올라가 모바일 데이터가 덜 든다.
 * - 브라우저가 못 읽는 형식(예: 크롬 데스크톱의 HEIC)은 서버가 변환하도록 그대로 보낸다.
 */
export async function preparePhoto(file: File): Promise<File> {
  const problem = checkPhotoCandidate(file)
  if (problem) throw new Error(problem)

  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  } catch {
    if (file.size <= MAX_PHOTO_BYTES) return file
    throw new Error('사진이 너무 커요. 10MB 이하의 JPEG로 저장해서 올려 주세요.')
  }

  try {
    const longEdge = Math.max(bitmap.width, bitmap.height)
    if (KEEP_AS_IS.has(file.type) && longEdge <= MAX_PHOTO_EDGE && file.size <= MAX_PHOTO_BYTES) return file

    const { width, height } = fitSize(bitmap.width, bitmap.height)
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) return file
    context.fillStyle = '#fff'
    context.fillRect(0, 0, width, height)
    context.drawImage(bitmap, 0, 0, width, height)

    for (const quality of [0.9, 0.8, 0.7]) {
      const blob = await toBlob(canvas, quality)
      if (blob && blob.size <= MAX_PHOTO_BYTES) {
        return new File([blob], 'photo.jpg', { type: 'image/jpeg' })
      }
    }
    throw new Error('사진이 너무 커요. 더 작은 사진으로 올려 주세요.')
  } finally {
    bitmap.close()
  }
}
