import { useCallback, useEffect, useState } from 'react'
import { deleteReview, fetchMyReview, fetchReviews, saveReview } from '../api/client.ts'
import type { PageResponse, Review, User } from '../api/types.ts'

const MAX_LENGTH = 1000

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

  const startEdit = useCallback(() => {
    setRating(mine?.rating ?? 0)
    setContent(mine?.content ?? '')
    setFormError(null)
    setEditing(true)
  }, [mine])

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
      setEditing(false)
      setReload((n) => n + 1)
      onChanged()
    } catch (e) {
      setFormError(e instanceof Error ? e.message : '리뷰를 저장하지 못했어요.')
    } finally {
      setSaving(false)
    }
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
          {formError && (
            <p className="notice notice--error" role="alert">
              {formError}
            </p>
          )}
          <div className="detail__links">
            <button type="submit" className="btn btn--small btn--primary" disabled={saving}>
              {saving ? '저장 중…' : '저장'}
            </button>
            <button type="button" className="btn btn--small" disabled={saving} onClick={() => setEditing(false)}>
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
    </section>
  )
}
