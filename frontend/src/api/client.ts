import type { BakeryDetail, BakerySummary, District, PageResponse } from './types.ts'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const res = await fetch(url, { signal, headers: { Accept: 'application/json' } })
  if (!res.ok) {
    // 백엔드는 ProblemDetail({ detail }) 형식으로 에러를 준다.
    let message = `요청에 실패했어요 (HTTP ${res.status})`
    try {
      const body = (await res.json()) as { detail?: string }
      if (body.detail) message = body.detail
    } catch {
      // 본문이 JSON이 아니면 기본 메시지를 쓴다.
    }
    throw new ApiError(res.status, message)
  }
  return (await res.json()) as T
}

export function fetchDistricts(signal?: AbortSignal): Promise<District[]> {
  return getJson<District[]>('/api/districts', signal)
}

/** @param query buildBakeryQuery()가 만든 쿼리 문자열 */
export function fetchBakeries(query: string, signal?: AbortSignal): Promise<PageResponse<BakerySummary>> {
  return getJson<PageResponse<BakerySummary>>(`/api/bakeries?${query}`, signal)
}

export function fetchBakery(id: number, signal?: AbortSignal): Promise<BakeryDetail> {
  return getJson<BakeryDetail>(`/api/bakeries/${id}`, signal)
}
