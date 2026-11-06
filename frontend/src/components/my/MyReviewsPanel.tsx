import { useState } from 'react'
import { Link } from 'react-router'
import { deleteReview, fetchMyReviews } from '../../api/client.ts'
import { useAsync } from '../../hooks/useAsync.ts'
import { formatDateTime } from '../../lib/format.ts'
import { Badge, ConfirmButton, ErrorNote } from '../admin/common.tsx'

export default function MyReviewsPanel() {
  const { data, loading, error, reload } = useAsync((signal) => fetchMyReviews(signal), 'reviews')
  const [actionError, setActionError] = useState<string | null>(null)

  async function remove(bakeryId: number) {
    setActionError(null)
    try {
      await deleteReview(bakeryId)
      reload()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '지우지 못했어요.')
    }
  }

  return (
    <section aria-labelledby="my-reviews-title">
      <h2 id="my-reviews-title">내 후기</h2>
      <ErrorNote message={error ?? actionError} />
      {loading && !data && <p className="summary">불러오는 중…</p>}
      {data && data.length === 0 && (
        <p className="notice">
          아직 쓴 후기가 없어요. <Link to="/">지도에서 빵집을 찾아 후기를 남겨 보세요.</Link>
        </p>
      )}
      <ul className="plain my-list" aria-busy={loading}>
        {data?.map((review) => (
          <li key={review.id} className="my-item">
            <div className="my-item__head">
              <strong>{review.bakery.name}</strong>
              <span className="stars" role="img" aria-label={`5점 만점에 ${review.rating}점`}>
                {'★'.repeat(review.rating)}
                <span className="stars__off">{'★'.repeat(5 - review.rating)}</span>
              </span>
              {review.hidden && <Badge tone="danger">신고로 숨겨짐</Badge>}
            </div>
            {review.content && <p className="review__content">{review.content}</p>}
            {review.photos.length > 0 && (
              <ul className="plain review__photos">
                {review.photos.map((photo) => (
                  <li key={photo.id}>
                    <a className="review__photo" href={photo.url} target="_blank" rel="noopener noreferrer">
                      <img src={photo.thumbUrl} alt="올린 사진" loading="lazy" />
                    </a>
                  </li>
                ))}
              </ul>
            )}
            <p className="card__meta">
              {review.bakery.district.name} · {formatDateTime(review.updatedAt)}
              {review.hidden && ' · 운영자가 확인 중이에요'}
            </p>
            <div className="detail__links">
              <Link className="btn btn--small" to={`/?bakery=${review.bakery.id}`}>
                빵집 보기·수정
              </Link>
              <ConfirmButton label="삭제" confirmLabel="정말 삭제" onConfirm={() => void remove(review.bakery.id)} />
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
