import type { BakeryReportKind, ReportReason, ReportTarget } from './types.ts'

export type AdminSummary = {
  pendingBakeryReports: number
  pendingContentReports: number
  hiddenReviews: number
  hiddenPhotos: number
  suspendedUsers: number
}

export type AdminBakeryReport = {
  id: number
  kind: BakeryReportKind
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  name: string | null
  address: string | null
  instagramUsername: string | null
  note: string | null
  adminNote: string | null
  bakeryId: number | null
  bakeryName: string | null
  bakeryStatus: string | null
  reporterId: number
  reporterNickname: string
  createdAt: string
  reviewedAt: string | null
}

export type ApproveBody = {
  lat?: number
  lng?: number
  districtCode?: string
  applyInstagram?: boolean
  adminNote?: string
}

export type AdminPhoto = { id: number; url: string; thumbUrl: string; status: 'VISIBLE' | 'HIDDEN' }

export type AdminContentTarget = {
  exists: boolean
  status: 'VISIBLE' | 'HIDDEN' | null
  hiddenReason: 'REPORTS' | 'ADMIN' | null
  reviewId: number | null
  bakeryId: number | null
  bakeryName: string | null
  authorId: number | null
  authorNickname: string | null
  rating: number | null
  content: string | null
  photos: AdminPhoto[]
}

export type AdminContentGroup = {
  targetType: ReportTarget
  targetId: number
  pendingCount: number
  totalCount: number
  reasons: ReportReason[]
  firstReportedAt: string
  lastReportedAt: string
  target: AdminContentTarget
  details: { reason: ReportReason; detail: string | null; reporterNickname: string; status: string; createdAt: string }[]
}

export type ContentAction = 'DISMISS' | 'HIDE' | 'DELETE' | 'RESTORE'

export type BakeryStatus = 'ACTIVE' | 'HIDDEN' | 'CLOSED' | 'PENDING'

export type AdminBakeryRow = {
  id: number
  name: string
  districtName: string
  roadAddress: string | null
  status: BakeryStatus
  instagramUsername: string | null
  hasOpeningHours: boolean
  reviewCount: number
  ratingAvg: number | null
  popularityScore: number
  kakaoPlaceId: string | null
  updatedAt: string
}

export type OpeningHours = Partial<Record<'mon' | 'tue' | 'wed' | 'thu' | 'fri' | 'sat' | 'sun', string>>

export type AdminBakeryDetail = {
  summary: AdminBakeryRow
  jibunAddress: string | null
  phone: string | null
  openingHours: OpeningHours | null
  lat: number
  lng: number
  kakaoMapUrl: string | null
  naverMapUrl: string | null
}

export type AdminUserRow = {
  id: number
  nickname: string
  loginId: string | null
  maskedEmail: string | null
  providers: string[]
  role: 'USER' | 'ADMIN'
  createdAt: string
  suspendedAt: string | null
  suspendedReason: string | null
  reviewCount: number
  photoCount: number
  reportsReceived: number
}

export type AdminUserDetail = {
  user: AdminUserRow
  recentReviews: {
    id: number
    bakeryId: number
    bakeryName: string
    rating: number
    content: string | null
    status: 'VISIBLE' | 'HIDDEN'
    createdAt: string
  }[]
}

export type AuditEntry = {
  id: number
  adminId: number | null
  adminNickname: string
  action: string
  targetType: string
  targetId: number | null
  detail: string | null
  createdAt: string
}
