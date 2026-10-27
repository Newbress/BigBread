import { useCallback, useEffect, useState } from 'react'
import { deleteCourse, fetchCourse, fetchCourses, saveCourse } from '../api/client.ts'
import type { BakerySummary, CourseSummary, RouteOrder, User } from '../api/types.ts'

type State = { userId: number | null; items: CourseSummary[]; error: string | null }

const EMPTY: State = { userId: null, items: [], error: null }

/** 내 코스(저장한 동선). 로그인한 사용자가 바뀌면 다시 불러온다. */
export function useCourses(user: User | null) {
  const userId = user?.id ?? null
  const [state, setState] = useState<State>(EMPTY)

  const reload = useCallback(
    (signal?: AbortSignal) =>
      fetchCourses(signal)
        .then((items) => setState({ userId, items, error: null }))
        .catch((e: unknown) => {
          if (signal?.aborted) return
          setState({ userId, items: [], error: e instanceof Error ? e.message : '코스를 불러오지 못했어요.' })
        }),
    [userId],
  )

  useEffect(() => {
    if (userId === null) return
    const controller = new AbortController()
    void reload(controller.signal)
    return () => controller.abort()
  }, [userId, reload])

  const current = state.userId === userId && userId !== null ? state : EMPTY
  const loading = userId !== null && state.userId !== userId

  /** 지금 만든 동선을 코스로 저장한다. 실패하면 서버 메시지를 담은 오류를 던진다. */
  const save = useCallback(
    async (title: string, order: RouteOrder, bakeryIds: number[]) => {
      await saveCourse({ title, order, bakeryIds })
      await reload()
    },
    [reload],
  )

  const remove = useCallback(
    async (id: number) => {
      await deleteCourse(id)
      setState((s) => ({ ...s, items: s.items.filter((course) => course.id !== id) }))
    },
    [],
  )

  /** 저장한 코스의 빵집을 저장한 순서 그대로 가져온다. */
  const loadStops = useCallback(async (id: number): Promise<BakerySummary[]> => (await fetchCourse(id)).stops, [])

  return { items: current.items, loading, error: current.error, save, remove, loadStops }
}
