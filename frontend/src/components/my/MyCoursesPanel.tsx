import { useState } from 'react'
import { Link } from 'react-router'
import { deleteCourse, fetchCourses } from '../../api/client.ts'
import { useAsync } from '../../hooks/useAsync.ts'
import { formatDateTime } from '../../lib/format.ts'
import { ConfirmButton, ErrorNote } from '../admin/common.tsx'

export default function MyCoursesPanel() {
  const { data, loading, error, reload } = useAsync((signal) => fetchCourses(signal), 'courses')
  const [actionError, setActionError] = useState<string | null>(null)

  async function remove(id: number) {
    setActionError(null)
    try {
      await deleteCourse(id)
      reload()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '지우지 못했어요.')
    }
  }

  return (
    <section aria-labelledby="my-courses-title">
      <h2 id="my-courses-title">내 코스</h2>
      <ErrorNote message={error ?? actionError} />
      {loading && !data && <p className="summary">불러오는 중…</p>}
      {data && data.length === 0 && (
        <p className="notice">
          저장한 코스가 없어요. <Link to="/?tab=route">지도에서 빵집을 담아 동선을 만들고 저장해 보세요.</Link>
        </p>
      )}
      <ul className="plain my-list" aria-busy={loading}>
        {data?.map((course) => (
          <li key={course.id} className="my-item">
            <div className="my-item__head">
              <strong>{course.title}</strong>
              <span className="card__meta">{course.stopCount}곳</span>
            </div>
            {course.description && <p className="card__meta">{course.description}</p>}
            <p className="card__meta">{course.stopNames.join(' → ')}</p>
            <p className="card__meta">{formatDateTime(course.createdAt)}</p>
            <div className="detail__links">
              <Link className="btn btn--small" to="/?tab=courses">
                지도에서 불러오기
              </Link>
              <ConfirmButton label="삭제" confirmLabel="정말 삭제" onConfirm={() => void remove(course.id)} />
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
