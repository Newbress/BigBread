import { readCookie } from '../lib/cookie.ts'
import type {
  AuthProvider,
  BakeryDetail,
  BakerySummary,
  District,
  Me,
  PageResponse,
  RouteRequest,
  RouteResponse,
} from './types.ts'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function requestJson<T>(url: string, init: RequestInit): Promise<T> {
  const res = await fetch(url, init)
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

function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  return requestJson<T>(url, { signal, headers: { Accept: 'application/json' } })
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

export function fetchProviders(signal?: AbortSignal): Promise<AuthProvider[]> {
  return getJson<AuthProvider[]>('/api/auth/providers', signal)
}

export function fetchMe(signal?: AbortSignal): Promise<Me> {
  return getJson<Me>('/api/me', signal)
}

/** 변경 요청은 CSRF 토큰(쿠키 XSRF-TOKEN)을 X-XSRF-TOKEN 헤더로 돌려보내야 한다. */
export async function logout(): Promise<void> {
  const token = readCookie('XSRF-TOKEN')
  const res = await fetch('/api/logout', {
    method: 'POST',
    headers: token ? { 'X-XSRF-TOKEN': token } : {},
  })
  if (!res.ok) throw new ApiError(res.status, '로그아웃에 실패했어요.')
}

export function planRoute(request: RouteRequest, signal?: AbortSignal): Promise<RouteResponse> {
  const body = {
    bakeryIds: request.bakeryIds,
    order: request.order,
    ...(request.start ? { start: request.start } : {}),
  }
  return requestJson<RouteResponse>('/api/routes', {
    method: 'POST',
    signal,
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}
