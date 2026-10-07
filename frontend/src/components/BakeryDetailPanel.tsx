import { useEffect, useState } from 'react'
import { fetchBakery } from '../api/client.ts'
import type { BakeryDetail } from '../api/types.ts'
import { formatPrice, isHttpUrl, PLATFORM_LABEL, toOpeningHoursRows } from '../lib/format.ts'

type Props = {
  id: number
  onBack: () => void
}

/** 어떤 빵집에 대한 응답인지 함께 저장해서, 현재 빵집의 응답이 왔는지(loading)를 계산한다. */
type Result = { id: number; detail: BakeryDetail | null; error: string | null }

function ExternalLink({ href, children }: { href: string | null; children: string }) {
  if (!isHttpUrl(href)) return null
  return (
    <a className="btn" href={href} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  )
}

export default function BakeryDetailPanel({ id, onBack }: Props) {
  const [result, setResult] = useState<Result | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    fetchBakery(id, controller.signal)
      .then((detail) => setResult({ id, detail, error: null }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setResult({
          id,
          detail: null,
          error: e instanceof Error ? e.message : '빵집 정보를 불러오지 못했어요.',
        })
      })

    return () => controller.abort()
  }, [id])

  const settled = result !== null && result.id === id
  const loading = !settled
  const detail = settled ? result.detail : null
  const error = settled ? result.error : null

  const hours = detail ? toOpeningHoursRows(detail.openingHours) : null
  const phone = detail?.phone?.replace(/[^\d+-]/g, '')
  const reviewLinks = detail ? detail.sourceLinks.filter((link) => isHttpUrl(link.url)) : []

  return (
    <article className="detail" aria-busy={loading}>
      <button type="button" className="btn btn--ghost" onClick={onBack}>
        ← 목록으로
      </button>

      {loading && <p className="summary">불러오는 중…</p>}
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}

      {detail && (
        <>
          <header>
            <h2>{detail.name}</h2>
            <p className="card__meta">
              {detail.district.name}
              {detail.roadAddress ? ` · ${detail.roadAddress}` : ''}
            </p>
            {phone && (
              <p>
                <a href={`tel:${phone}`}>{detail.phone}</a>
              </p>
            )}
          </header>

          <div className="detail__links">
            <ExternalLink href={detail.links.kakaoMap}>카카오맵에서 보기</ExternalLink>
            <ExternalLink href={detail.links.instagram}>인스타그램</ExternalLink>
          </div>

          <section aria-labelledby="breads-title">
            <h3 id="breads-title">빵</h3>
            {detail.breads.length === 0 ? (
              <p className="notice">아직 등록된 빵이 없어요.</p>
            ) : (
              <ul className="plain">
                {detail.breads.map((bread) => (
                  <li key={bread.id} className="bread">
                    <span>
                      {bread.signature && <span className="tag tag--star">대표</span>} {bread.name}
                      {bread.description && <span className="card__meta"> — {bread.description}</span>}
                    </span>
                    <span className="bread__price">{formatPrice(bread.price)}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {hours && (
            <section aria-labelledby="hours-title">
              <h3 id="hours-title">영업시간</h3>
              <dl className="hours">
                {hours.map((row) => (
                  <div key={row.day} className={row.today ? 'hours__row hours__row--today' : 'hours__row'}>
                    <dt>{row.day}</dt>
                    <dd>{row.text}</dd>
                  </div>
                ))}
              </dl>
            </section>
          )}

          <section aria-labelledby="links-title">
            <h3 id="links-title">원본 후기</h3>
            {reviewLinks.length === 0 ? (
              <p className="notice">아직 수집된 후기가 없어요.</p>
            ) : (
              <ul className="plain">
                {reviewLinks.map((link) => (
                  <li key={link.id}>
                    <a href={link.url} target="_blank" rel="noopener noreferrer">
                      {link.title ?? link.url}
                    </a>
                    <span className="card__meta">
                      {' '}
                      · {PLATFORM_LABEL[link.platform]}
                      {link.publishedAt && ` · ${new Date(link.publishedAt).toLocaleDateString('ko-KR')}`}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </article>
  )
}
