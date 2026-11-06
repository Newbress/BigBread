/**
 * 약관·개인정보 처리방침에 표시되는 운영자 정보.
 * 서비스를 열기 전에 실제 값으로 바꾸세요. (비어 있으면 화면에 "미설정" 경고가 보입니다.)
 */
export const OPERATOR = {
  /** 운영자(개인 또는 사업자) 이름 */
  name: '',
  /** 이용자 문의·개인정보 요청을 받을 이메일 */
  contactEmail: '',
  /** 시행일 (YYYY-MM-DD) */
  effectiveDate: '2026-11-06',
}

export const operatorConfigured = (): boolean => OPERATOR.name.trim() !== '' && OPERATOR.contactEmail.trim() !== ''
