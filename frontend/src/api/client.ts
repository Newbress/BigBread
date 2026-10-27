import { readCookie } from '../lib/cookie.ts'
import type {
  AuthOptions,
  AuthProvider,
  BakeryDetail,
  BakerySummary,
  District,
  Me,
  Message,
  PageResponse,
  RouteRequest,
  RouteResponse,
  SignupRequest,
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
  // 본문이 없는 성공(204)은 값이 없다.
  if (res.status === 204) return undefined as T
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

/**
 * 변경 요청(POST)은 CSRF 토큰이 필요하다: 쿠키 XSRF-TOKEN 값을 X-XSRF-TOKEN 헤더로 돌려보낸다.
 * 쿠키가 아직 없으면(예: 메일 링크로 바로 들어온 페이지) 아무 GET으로 먼저 받아 온다.
 */
async function postJson<T>(url: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  let token = readCookie('XSRF-TOKEN')
  if (!token) {
    await fetch('/api/me', { signal })
    token = readCookie('XSRF-TOKEN')
  }
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) headers['X-XSRF-TOKEN'] = token
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  return requestJson<T>(url, { method: 'POST', signal, headers, body: body === undefined ? undefined : JSON.stringify(body) })
}

export async function logout(): Promise<void> {
  await postJson<void>('/api/logout')
}

export function fetchAuthOptions(signal?: AbortSignal): Promise<AuthOptions> {
  return getJson<AuthOptions>('/api/auth/options', signal)
}

export function checkLoginId(loginId: string, signal?: AbortSignal): Promise<{ available: boolean }> {
  return getJson<{ available: boolean }>(`/api/auth/id-available?loginId=${encodeURIComponent(loginId)}`, signal)
}

export function signup(request: SignupRequest): Promise<Message> {
  return postJson<Message>('/api/auth/signup', request)
}

export function verifyEmail(token: string, signal?: AbortSignal): Promise<void> {
  return postJson<void>('/api/auth/verify-email', { token }, signal)
}

export function loginLocal(loginId: string, password: string): Promise<Me> {
  return postJson<Me>('/api/auth/login', { loginId, password })
}

export function forgotPassword(loginId: string): Promise<Message> {
  return postJson<Message>('/api/auth/password/forgot', { loginId })
}

export function resetPassword(token: string, newPassword: string): Promise<void> {
  return postJson<void>('/api/auth/password/reset', { token, newPassword })
}

export function findLoginId(email: string): Promise<Message> {
  return postJson<Message>('/api/auth/find-id', { email })
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
