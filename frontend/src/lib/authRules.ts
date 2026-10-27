/**
 * 가입 입력 규칙 (서버와 같은 규칙으로 입력하는 동안 바로 알려주는 용도).
 * 최종 판단은 항상 서버가 하고, 서버만 아는 규칙(흔한 비밀번호 목록 등)은 서버 응답 메시지로 보여준다.
 */

export const PASSWORD_MIN = 10
export const PASSWORD_MAX = 128
const MIN_DISTINCT_CHARS = 5

const LOGIN_ID = /^[a-z][a-z0-9]{4,19}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const RESERVED_LOGIN_IDS = new Set([
  'admin',
  'administrator',
  'root',
  'system',
  'support',
  'bigbread',
  'master',
  'manager',
  'service',
  'staff',
  'operator',
  'official',
  'webmaster',
  'postmaster',
  'anonymous',
  'guest',
])

/** 아이디는 대소문자를 구분하지 않으므로 소문자로 통일한다. */
export function normalizeLoginId(value: string): string {
  return value.toLowerCase()
}

export function loginIdError(value: string): string | null {
  const id = normalizeLoginId(value)
  if (!LOGIN_ID.test(id)) return '아이디는 영문으로 시작하는 영문·숫자 5~20자로 입력해 주세요.'
  if (RESERVED_LOGIN_IDS.has(id)) return '사용할 수 없는 아이디예요.'
  return null
}

export function emailError(value: string): string | null {
  const email = value.trim()
  if (email.length > 255 || !EMAIL.test(email)) return '이메일 형식이 올바르지 않아요.'
  return null
}

export function nicknameError(value: string): string | null {
  const nickname = value.trim()
  if (nickname.length < 2 || nickname.length > 20) return '닉네임은 2~20자로 입력해 주세요.'
  return null
}

export type PasswordChecks = {
  length: boolean
  letter: boolean
  digit: boolean
  special: boolean
  distinct: boolean
}

export function checkPassword(password: string): PasswordChecks {
  const chars = Array.from(password)
  return {
    length: chars.length >= PASSWORD_MIN && chars.length <= PASSWORD_MAX,
    letter: chars.some((c) => /\p{L}/u.test(c)),
    digit: chars.some((c) => /\p{Nd}/u.test(c)),
    special: chars.some((c) => !/[\p{L}\p{Nd}\s]/u.test(c) && !/\p{Cc}/u.test(c)),
    distinct: new Set(chars).size >= MIN_DISTINCT_CHARS,
  }
}

export function passwordError(password: string, loginId: string): string | null {
  const checks = checkPassword(password)
  if (!checks.length) return `비밀번호는 ${PASSWORD_MIN}~${PASSWORD_MAX}자로 입력해 주세요.`
  if (!checks.letter || !checks.digit || !checks.special) {
    return '비밀번호에는 영문, 숫자, 특수문자를 각각 1개 이상 넣어 주세요.'
  }
  const id = normalizeLoginId(loginId)
  if (id && password.toLowerCase().includes(id)) return '비밀번호에 아이디를 포함할 수 없어요.'
  if (!checks.distinct) return `서로 다른 문자를 ${MIN_DISTINCT_CHARS}개 이상 써 주세요.`
  return null
}
