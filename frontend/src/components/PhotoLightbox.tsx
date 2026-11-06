import { useEffect, useRef, useState } from 'react'
import type { ReviewPhoto } from '../api/types.ts'
import ContentReportForm from './ContentReportForm.tsx'

type Props = {
  photo: ReviewPhoto
  alt: string
  onClose: () => void
  /** 남이 올린 사진이면 신고할 수 있다 */
  reportable?: boolean
  /** 로그인하지 않았으면 신고 대신 로그인 창을 여는 동작 */
  onLogin?: () => void
}

/** 사진을 크게 보는 창. 바깥을 누르거나 Esc로 닫는다. */
export default function PhotoLightbox({ photo, alt, onClose, reportable = false, onLogin }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const [reporting, setReporting] = useState(false)

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
      {reportable && (
        <div className="lightbox__report">
          {reporting ? (
            <ContentReportForm
              targetType="PHOTO"
              targetId={photo.id}
              onDone={() => ref.current?.close()}
              onCancel={() => setReporting(false)}
            />
          ) : (
            <button type="button" className="btn btn--small" onClick={() => (onLogin ? onLogin() : setReporting(true))}>
              이 사진 신고
            </button>
          )}
        </div>
      )}
    </dialog>
  )
}
