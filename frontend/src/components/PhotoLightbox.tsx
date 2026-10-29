import { useEffect, useRef } from 'react'
import type { ReviewPhoto } from '../api/types.ts'

type Props = { photo: ReviewPhoto; alt: string; onClose: () => void }

/** 사진을 크게 보는 창. 바깥을 누르거나 Esc로 닫는다. */
export default function PhotoLightbox({ photo, alt, onClose }: Props) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  return (
    <dialog
      ref={ref}
      className="lightbox"
      aria-label="사진 크게 보기"
      onClose={onClose}
      onClick={(e) => {
        if (e.target === ref.current) ref.current?.close()
      }}
    >
      <img src={photo.url} alt={alt} width={photo.width} height={photo.height} />
      <button type="button" className="lightbox__close" aria-label="닫기" onClick={() => ref.current?.close()}>
        ×
      </button>
    </dialog>
  )
}
