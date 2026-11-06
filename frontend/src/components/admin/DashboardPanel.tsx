import { Link } from 'react-router'
import type { AdminSummary } from '../../api/adminTypes.ts'

type Props = { summary: AdminSummary | null; error: string | null }

const CARDS: { key: keyof AdminSummary; label: string; to: string; urgent: boolean }[] = [
  { key: 'pendingBakeryReports', label: '확인할 빵집 제보', to: '/admin/bakery-reports', urgent: true },
  { key: 'pendingContentReports', label: '확인할 신고', to: '/admin/content-reports', urgent: true },
  { key: 'hiddenReviews', label: '숨겨진 리뷰', to: '/admin/content-reports?status=HANDLED', urgent: false },
  { key: 'hiddenPhotos', label: '숨겨진 사진', to: '/admin/content-reports?status=HANDLED', urgent: false },
  { key: 'suspendedUsers', label: '정지된 사용자', to: '/admin/users?suspended=1', urgent: false },
]

export default function DashboardPanel({ summary, error }: Props) {
  return (
    <section aria-labelledby="dash-title">
      <h2 id="dash-title">처리할 일</h2>
      {error && (
        <p className="notice notice--error" role="alert">
          {error}
        </p>
      )}
      <ul className="plain admin-cards">
        {CARDS.map((card) => {
          const value = summary?.[card.key]
          return (
            <li key={card.key}>
              <Link to={card.to} className={card.urgent && value ? 'admin-card admin-card--urgent' : 'admin-card'}>
                <span className="admin-card__value">{value ?? '–'}</span>
                <span>{card.label}</span>
              </Link>
            </li>
          )
        })}
      </ul>
      <p className="card__meta">
        신고가 3명 쌓이면 리뷰·사진은 확인할 때까지 자동으로 숨겨져요. 숨겨진 것은 신고 화면의 &lsquo;처리됨&rsquo;에서 다시 풀 수 있어요.
      </p>
    </section>
  )
}
