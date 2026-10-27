import { useState } from 'react'
import type { CourseSummary } from '../api/types.ts'

type Props = {
  loggedIn: boolean
  onLogin: () => void
  courses: CourseSummary[]
  loading: boolean
  error: string | null
  /** 저장한 순서 그대로 내 동선으로 불러온다 */
  onLoad: (id: number) => Promise<void>
  onDelete: (id: number) => Promise<void>
}

/** 내 코스(저장한 동선) 목록. 불러오기와 삭제(한 번 더 확인)를 할 수 있다. */
export default function CoursesPanel({ loggedIn, onLogin, courses, loading, error, onLoad, onDelete }: Props) {
  const [confirmingId, setConfirmingId] = useState<number | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  if (!loggedIn) {
    return (
      <section aria-label="내 코스">
        <p className="notice">로그인하면 만든 동선을 코스로 저장해 두고 다시 불러올 수 있어요.</p>
        <button type="button" className="btn btn--on btn--block" onClick={onLogin}>
          로그인
        </button>
      </section>
    )
  }

  const run = async (id: number, action: (id: number) => Promise<void>) => {
    setBusyId(id)
    setActionError(null)
    try {
      await action(id)
      setConfirmingId(null)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '요청에 실패했어요.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section aria-label="내 코스" aria-busy={loading}>
      <p className="summary" aria-live="polite">
        {loading ? '불러오는 중…' : `${courses.length}개`}
      </p>
      {(error || actionError) && (
        <p className="notice notice--error" role="alert">
          {actionError ?? error}
        </p>
      )}
      {!loading && !error && courses.length === 0 && (
        <p className="notice">저장한 코스가 없어요. &lsquo;내 동선&rsquo;에서 동선을 만든 뒤 &lsquo;코스로 저장&rsquo;을 눌러 보세요.</p>
      )}
      <ul className="plain courses">
        {courses.map((course) => (
          <li key={course.id} className="course-card">
            <h3>{course.title}</h3>
            {course.description && <p className="card__meta">{course.description}</p>}
            <p className="card__meta">
              {course.stopCount}곳 · {new Date(course.createdAt).toLocaleDateString('ko-KR')}
            </p>
            <p className="course-card__stops">{course.stopNames.join(' → ')}</p>
            <div className="course-card__actions">
              <button
                type="button"
                className="btn btn--small btn--on"
                disabled={busyId === course.id || course.stopCount === 0}
                onClick={() => run(course.id, onLoad)}
              >
                {busyId === course.id ? '불러오는 중…' : '동선으로 불러오기'}
              </button>
              {confirmingId === course.id ? (
                <>
                  <button type="button" className="btn btn--small btn--danger" disabled={busyId === course.id} onClick={() => run(course.id, onDelete)}>
                    지우기
                  </button>
                  <button type="button" className="btn btn--small" onClick={() => setConfirmingId(null)}>
                    취소
                  </button>
                </>
              ) : (
                <button type="button" className="btn btn--small" onClick={() => setConfirmingId(course.id)}>
                  삭제
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
