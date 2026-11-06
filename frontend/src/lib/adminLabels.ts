import type { BakeryReportKind, ReportReason } from '../api/types.ts'
import type { BakeryStatus } from '../api/adminTypes.ts'

export const REPORT_KIND_LABEL: Record<BakeryReportKind, string> = {
  NEW: '새 빵집',
  CLOSED: '폐업·이전',
  WRONG_INFO: '정보 오류',
}

export const REPORT_REASON_LABEL: Record<ReportReason, string> = {
  SPAM: '광고·도배',
  ABUSE: '욕설·비방',
  PRIVACY: '개인정보 노출',
  INAPPROPRIATE: '부적절한 내용',
  OTHER: '기타',
}

export const BAKERY_STATUS_LABEL: Record<BakeryStatus, string> = {
  ACTIVE: '표시 중',
  HIDDEN: '숨김',
  CLOSED: '폐업',
  PENDING: '대기',
}

export const REPORT_STATUS_LABEL = { PENDING: '확인 중', APPROVED: '승인', REJECTED: '반려' } as const

const AUDIT_ACTION_LABEL: Record<string, string> = {
  BAKERY_REPORT_APPROVE: '제보 승인',
  BAKERY_REPORT_REJECT: '제보 반려',
  CONTENT_REPORT_DISMISS: '신고 문제없음',
  CONTENT_REPORT_HIDE: '신고 숨김',
  CONTENT_REPORT_DELETE: '신고 삭제',
  CONTENT_REPORT_RESTORE: '숨김 해제',
  BAKERY_UPDATE: '빵집 수정',
  USER_SUSPEND: '사용자 정지',
  USER_UNSUSPEND: '정지 해제',
  USER_HIDE_CONTENT: '글 일괄 숨김',
}

export function auditActionLabel(action: string): string {
  return AUDIT_ACTION_LABEL[action] ?? action
}

export const AUDIT_TARGET_LABEL: Record<string, string> = {
  BAKERY_REPORT: '빵집 제보',
  REVIEW: '리뷰',
  PHOTO: '사진',
  BAKERY: '빵집',
  USER: '사용자',
}
