import { useEffect, useMemo, useState } from 'react'
import { fetchBakeries } from '../api/client.ts'
import type { BakerySummary, PageResponse, SearchParams } from '../api/types.ts'
import { buildBakeryQuery } from '../lib/query.ts'

type State = {
  data: PageResponse<BakerySummary> | null
  loading: boolean
  error: string | null
}

/** 어떤 쿼리에 대한 응답인지 함께 저장해서, 현재 쿼리의 응답이 왔는지(loading)를 계산한다. */
type Result = {
  query: string
  refreshKey: number
  data: PageResponse<BakerySummary> | null
  error: string | null
}

/**
 * 검색 조건이 바뀔 때마다 목록을 다시 불러오고, 이전 요청은 취소한다.
 * 새 응답이 오기 전까지는 이전 목록을 유지해서 지도 마커가 깜빡이지 않게 한다.
 */
/** refreshKey가 바뀌면 같은 조건으로 다시 불러온다 (리뷰로 평점·순서가 바뀐 경우 등). */
export function useBakeries(params: SearchParams, refreshKey = 0): State {
  const query = useMemo(() => buildBakeryQuery(params), [params])
  const [result, setResult] = useState<Result | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    fetchBakeries(query, controller.signal)
      .then((data) => setResult({ query, refreshKey, data, error: null }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setResult((prev) => ({
          query,
          refreshKey,
          data: prev?.data ?? null,
          error: e instanceof Error ? e.message : '빵집 목록을 불러오지 못했어요.',
        }))
      })

    return () => controller.abort()
  }, [query, refreshKey])

  const settled = result !== null && result.query === query && result.refreshKey === refreshKey
  return {
    data: result?.data ?? null,
    loading: !settled,
    error: settled ? result.error : null,
  }
}
