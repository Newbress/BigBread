import { getJson, sendJson } from './client.ts'
import type {
  AdminBakeryDetail,
  AdminBakeryReport,
  AdminBakeryRow,
  AdminContentGroup,
  AdminSummary,
  AdminUserDetail,
  AdminUserRow,
  ApproveBody,
  PlaceCandidate,
  AuditEntry,
  ContentAction,
} from './adminTypes.ts'
import type { PageResponse, ReportTarget } from './types.ts'

const enc = encodeURIComponent

function qs(params: Record<string, string | number | boolean | null | undefined>): string {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined && v !== '' && v !== false)
    .map(([k, v]) => `${k}=${enc(String(v))}`)
  return parts.length ? `?${parts.join('&')}` : ''
}

export const fetchAdminSummary = (signal?: AbortSignal) => getJson<AdminSummary>('/api/admin/summary', signal)

export const fetchBakeryReports = (status: string, page: number, signal?: AbortSignal) =>
  getJson<PageResponse<AdminBakeryReport>>(`/api/admin/bakery-reports${qs({ status, page })}`, signal)

export const approveBakeryReport = (id: number, body: ApproveBody) =>
  sendJson<AdminBakeryReport>('POST', `/api/admin/bakery-reports/${id}/approve`, body)

export const rejectBakeryReport = (id: number, adminNote: string) =>
  sendJson<AdminBakeryReport>('POST', `/api/admin/bakery-reports/${id}/reject`, { adminNote })

export const fetchContentReports = (status: 'PENDING' | 'HANDLED', page: number, signal?: AbortSignal) =>
  getJson<PageResponse<AdminContentGroup>>(`/api/admin/content-reports${qs({ status, page })}`, signal)

export const resolveContentReport = (targetType: ReportTarget, targetId: number, action: ContentAction) =>
  sendJson<void>('POST', '/api/admin/content-reports/resolve', { targetType, targetId, action })

export const searchAdminBakeries = (q: string, status: string, page: number, signal?: AbortSignal) =>
  getJson<PageResponse<AdminBakeryRow>>(`/api/admin/bakeries${qs({ q, status, page })}`, signal)

export const fetchAdminBakery = (id: number, signal?: AbortSignal) =>
  getJson<AdminBakeryDetail>(`/api/admin/bakeries/${id}`, signal)

export const updateAdminBakery = (id: number, body: Record<string, unknown>) =>
  sendJson<AdminBakeryDetail>('PATCH', `/api/admin/bakeries/${id}`, body)

export const searchAdminUsers = (q: string, suspended: boolean, page: number, signal?: AbortSignal) =>
  getJson<PageResponse<AdminUserRow>>(`/api/admin/users${qs({ q, suspended, page })}`, signal)

export const fetchAdminUser = (id: number, signal?: AbortSignal) =>
  getJson<AdminUserDetail>(`/api/admin/users/${id}`, signal)

export const suspendUser = (id: number, reason: string) =>
  sendJson<AdminUserDetail>('POST', `/api/admin/users/${id}/suspend`, { reason })

export const unsuspendUser = (id: number) => sendJson<AdminUserDetail>('POST', `/api/admin/users/${id}/unsuspend`, {})

export const hideUserContent = (id: number, reason: string) =>
  sendJson<AdminUserDetail>('POST', `/api/admin/users/${id}/hide-content`, { reason })

export const fetchAuditLogs = (targetType: string, page: number, signal?: AbortSignal) =>
  getJson<PageResponse<AuditEntry>>(`/api/admin/audit-logs${qs({ targetType, page })}`, signal)

/** 새 빵집 제보의 위치를 정할 후보를 카카오에서 찾는다. */
export const searchPlaces = (query: string, signal?: AbortSignal) =>
  getJson<PlaceCandidate[]>(`/api/admin/bakery-reports/place-search${qs({ query })}`, signal)
