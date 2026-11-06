import { useCallback, useEffect, useRef, useState } from 'react'
import {
  deleteReview,
  deleteReviewPhoto,
  fetchMyReview,
  fetchReviews,
  saveReview,
  uploadReviewPhoto,
} from '../api/client.ts'
import type { PageResponse, Review, ReviewPhoto, User } from '../api/types.ts'
import { MAX_PHOTOS, preparePhoto } from '../lib/photo.ts'
import PhotoLightbox from './PhotoLightbox.tsx'

const MAX_LENGTH = 1000

/** 아직 올리지 않은 사진 (미리보기 주소는 쓰고 나면 반드시 해제한다). */
type PendingPhoto = { key: number; file: File; previewUrl: string }

type Props = {
  bakeryId: number
  user: User | null
  onLogin: () => void
  /** 리뷰를 쓰거나 지워서 평점이 바뀌었을 때 (상세의 평균을 다시 불러오게 한다) */
  onChanged: () => void
}

function Stars({ value }: { value: number }) {
  return (
    <span className="stars" role="img" aria-label={`5점 만점에 ${value}점`}>
      {'★'.repeat(value)}
      <span className="stars__off">{'★'.repeat(5 - value)}</span>
    </span>
  )
}

function RatingInput({ value, onChange }: { value: number; onChange: (rating: number) => void }) {
  return (
    <div className="rating-input" role="radiogroup" aria-label="평점">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={value === n}
          aria-label={`${n}점`}
          className={n <= value ? 'rating-input__star rating-input__star--on' : 'rating-input__star'}
          onClick={() => onChange(n)}
        >
          ★
        </button>
      ))}
    </div>
  )
}

/** 상세 화면의 리뷰: 목록(누구나) + 내 리뷰 쓰기·수정·삭제(로그인). */
export default function ReviewSection({ bakeryId, user, onLogin, onChanged }: Props) {
  const userId = user?.id ?? null
  const [reload, setReload] = useState(0)
  const [pages, setPages] = useState<PageResponse<Review> | null>(null)
  const [extra, setExtra] = useState<Review[]>([])
  const [mine, setMine] = useState<Review | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [rating, setRating] = useState(0)
  const [content, setContent] = useState('')
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [keptPhotos, setKeptPhotos] = useState<ReviewPhoto[]>([])
  const [removedIds, setRemovedIds] = useState<number[]>([])
  const [pending, setPending] = useState<PendingPhoto[]>([])
  const [preparing, setPreparing] = useState(false)
  const [lightbox, setLightbox] = useState<{ photo: ReviewPhoto; alt: string } | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const nextKey = useRef(0)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([fetchReviews(bakeryId, 0, controller.signal), userId === null ? null : fetchMyReview(bakeryId, controller.signal)])
      .then(([page, myReview]) => {
        setPages(page)
        setExtra([])
        setMine(myReview)
        setLoadError(null)
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setLoadError(e instanceof Error ? e.message : '리뷰를 불러오지 못했어요.')
      })
    return () => controller.abort()
  }, [bakeryId, userId, reload])

  const clearPending = useCallback((photos: PendingPhoto[]) => {
    photos.forEach((p) => URL.revokeObjectURL(p.previewUrl))
  }, [])

  // 화면을 떠날 때 미리보기 주소를 해제한다.
  const pendingRef = useRef(pending)
  useEffect(() => {
    pendingRef.current = pending
  }, [pending])
  useEffect(() => () => pendingRef.current.forEach((p) => URL.revokeObjectURL(p.previewUrl)), [])

  const startEdit = useCallback(() => {
    setRating(mine?.rating ?? 0)
    setContent(mine?.content ?? '')
    setKeptPhotos(mine?.photos ?? [])
    setRemovedIds([])
    setPending((prev) => {
      prev.forEach((p) => URL.revokeObjectURL(p.previewUrl))
      return []
    })
    setFormError(null)
    setEditing(true)
  }, [mine])

  function cancelEdit() {
    clearPending(pending)
    setPending([])
    setEditing(false)
  }

  async function addFiles(files: FileList | null) {
    if (!files || files.length === 0) return
    const room = MAX_PHOTOS - keptPhotos.length - pending.length
    const chosen = Array.from(files)
    if (chosen.length > room) {
      setFormError(`사진은 최대 ${MAX_PHOTOS}장까지예요. ${room > 0 ? `${room}장만 추가했어요.` : ''}`)
    } else {
      setFormError(null)
    }
    setPreparing(true)
    const added: PendingPhoto[] = []
    for (const file of chosen.slice(0, Math.max(room, 0))) {
      try {
        const prepared = await preparePhoto(file)
        added.push({ key: nextKey.current++, file: prepared, previewUrl: URL.createObjectURL(prepared) })
      } catch (e) {
        setFormError(e instanceof Error ? e.message : '사진을 준비하지 못했어요.')
      }
    }
    setPending((prev) => [...prev, ...added])
    setPreparing(false)
    if (fileInput.current) fileInput.current.value = ''
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (rating < 1) {
      setFormError('별점을 골라 주세요.')
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      await saveReview(bakeryId, rating, content)
    } catch (e) {
      setFormError(e instanceof Error ? e.message : '리뷰를 저장하지 못했어요.')
      setSaving(false)
      return
    }

    // 리뷰는 저장됐다. 사진은 지울 것을 지우고 새 사진을 한 장씩 올린다.
    const failed: { photo: PendingPhoto; reason: string }[] = []
    try {
      for (const id of removedIds) await deleteReviewPhoto(bakeryId, id)
    } catch (e) {
      setFormError(`리뷰는 저장했지만 사진을 지우지 못했어요: ${e instanceof Error ? e.message : ''}`)
    }
    for (const photo of pending) {
      try {
        await uploadReviewPhoto(bakeryId, photo.file)
        URL.revokeObjectURL(photo.previewUrl)
      } catch (e) {
        failed.push({ photo, reason: e instanceof Error ? e.message : '올리지 못했어요.' })
      }
    }
    setReload((n) => n + 1)
    onChanged()
    setSaving(false)
    if (failed.length === 0) {
      setPending([])
      setEditing(false)
      return
    }
    // 올리지 못한 사진은 남겨 두고 이유를 보여준다 (리뷰 글은 이미 저장됨). 올라간 사진은 다시 불러온 뒤 보인다.
    setPending(failed.map((f) => f.photo))
    setRemovedIds([])
    setKeptPhotos((await fetchMyReview(bakeryId).catch(() => null))?.photos ?? [])
    setFormError(`리뷰는 저장했지만 사진 ${failed.length}장을 올리지 못했어요: ${failed[0].reason}`)
  }

  async function remove() {
    setSaving(true)
    setFormError(null)
    try {
      await deleteReview(bakeryId)
      setConfirmingDelete(false)
      setReload((n) => n + 1)
      onChanged()
    } catch (e) {
      setFormError(e instanceof Error ? e.message : '리뷰를 지우지 못했어요.')
    } finally {
      setSaving(false)
    }
  }

  async function loadMore() {
    if (!pages) return
    setLoadingMore(true)
    try {
      const next = await fetchReviews(bakeryId, pages.page + 1)
      setPages(next)
      setExtra((prev) => [...prev, ...pages.items])
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : '리뷰를 더 불러오지 못했어요.')
    } finally {
      setLoadingMore(false)
    }
  }

  const items = pages ? [...extra, ...pages.items] : []
  const hasMore = pages !== null && pages.page + 1 < pages.totalPages

  return (
    <section aria-labelledby="reviews-title" className="reviews">
      <h3 id="reviews-title">
        리뷰 {pages ? `(${pages.totalElements})` : ''}
      </h3>

      {user === null ? (
        <p>
          <button type="button" className="btn btn--small" onClick={onLogin}>
            로그인하고 리뷰 쓰기
          </button>
        </p>
      ) : editing ? (
        <form className="review-form" onSubmit={submit}>
          <RatingInput value={rating} onChange={setRating} />
          <label className="review-form__label" htmlFor="review-content">
            후기 (선택)
          </label>
          <textarea
            id="review-content"
            value={content}
            maxLength={MAX_LENGTH}
            rows={4}
            placeholder="어떤 빵이 맛있었는지, 분위기는 어땠는지 알려 주세요."
            onChange={(e) => setContent(e.target.value)}
          />
          <p className="card__meta">
            {content.length} / {MAX_LENGTH}
          </p>
          <div className="photo-edit">
            <ul className="plain photo-edit__list">
              {keptPhotos
                .filter((p) => !removedIds.includes(p.id))
                .map((photo) => (
                  <li key={photo.id} className="photo-edit__item">
                    <img src={photo.thumbUrl} alt="올려 둔 사진" />
                    <button
                      type="button"
                      className="photo-edit__remove"
                      aria-label="이 사진 빼기"
                      onClick={() => setRemovedIds((ids) => [...ids, photo.id])}
                    >
                      ×
                    </button>
                  </li>
                ))}
              {pending.map((photo) => (
                <li key={photo.key} className="photo-edit__item">
                  <img src={photo.previewUrl} alt="새로 올릴 사진" />
                  <button
                    type="button"
                    className="photo-edit__remove"
                    aria-label="이 사진 빼기"
                    onClick={() => {
                      URL.revokeObjectURL(photo.previewUrl)
                      setPending((prev) => prev.filter((p) => p.key !== photo.key))
                    }}
                  >
                    ×
                  </button>
                </li>
              ))}
            </ul>
            {keptPhotos.filter((p) => !removedIds.includes(p.id)).length + pending.length < MAX_PHOTOS && (
              <>
                <input
                  ref={fileInput}
                  id="review-photos"
                  className="visually-hidden"
                  type="file"
                  accept="image/*,.heic,.heif"
                  multiple
                  disabled={preparing || saving}
                  onChange={(e) => void addFiles(e.target.files)}
                />
                <label htmlFor="review-photos" className="btn btn--small">
                  {preparing ? '사진 준비 중…' : '사진 추가'}
                </label>
              </>
            )}
            <p className="card__meta">
              사진은 최대 {MAX_PHOTOS}장 · 올릴 때 위치 정보는 지워져요.
            </p>
          </div>
          {formError && (
            <p className="notice notice--error" role="alert">
              {formError}
            </p>
          )}
          <div className="detail__links">
            <button type="submit" className="btn btn--small btn--primary" disabled={saving}>
              {saving ? '저장 중…' : '저장'}
            </button>
            <button type="button" className="btn btn--small" disabled={saving} onClick={cancelEdit}>
              취소
            </button>
          </div>
        </form>
      ) : mine === null ? (
        <p>
          <button type="button" className="btn btn--small" onClick={startEdit}>
            리뷰 쓰기
          </button>
        </p>
      ) : null}

      {loadError && (
        <p className="notice notice--error" role="alert">
          {loadError}
        </p>
      )}
      {pages && items.length === 0 && <p className="notice">아직 리뷰가 없어요. 첫 리뷰를 남겨 보세요.</p>}

      <ul className="plain review-list">
        {items.map((review) => (
          <li key={review.id} className="review">
            <div className="review__head">
              <Stars value={review.rating} />
              <span className="review__who">
                {review.nickname}
                {review.mine && <span className="tag"> 내 리뷰</span>}
              </span>
              <span className="card__meta">{new Date(review.createdAt).toLocaleDateString('ko-KR')}</span>
            </div>
            {review.content && <p className="review__content">{review.content}</p>}
            {review.photos.length > 0 && (
              <ul className="plain review__photos">
                {review.photos.map((photo, index) => (
                  <li key={photo.id}>
                    <button
                      type="button"
                      className="review__photo"
                      aria-label={`${review.nickname}님의 사진 ${index + 1} 크게 보기`}
                      onClick={() => setLightbox({ photo, alt: `${review.nickname}님의 리뷰 사진 ${index + 1}` })}
                    >
                      <img src={photo.thumbUrl} alt="" loading="lazy" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {review.mine && !editing && (
              <div className="detail__links">
                <button type="button" className="btn btn--small" onClick={startEdit}>
                  수정
                </button>
                {confirmingDelete ? (
                  <>
                    <button type="button" className="btn btn--small btn--danger" disabled={saving} onClick={remove}>
                      정말 삭제
                    </button>
                    <button type="button" className="btn btn--small" onClick={() => setConfirmingDelete(false)}>
                      취소
                    </button>
                  </>
                ) : (
                  <button type="button" className="btn btn--small" onClick={() => setConfirmingDelete(true)}>
                    삭제
                  </button>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>
      {!editing && formError && (
        <p className="notice notice--error" role="alert">
          {formError}
        </p>
      )}
      {hasMore && (
        <button type="button" className="btn btn--small" disabled={loadingMore} onClick={loadMore}>
          {loadingMore ? '불러오는 중…' : '리뷰 더 보기'}
        </button>
      )}
      {lightbox && <PhotoLightbox photo={lightbox.photo} alt={lightbox.alt} onClose={() => setLightbox(null)} />}
    </section>
  )
}
