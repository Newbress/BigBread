import { describe, expect, it } from 'vitest'
import { checkPassword, emailError, loginIdError, nicknameError, normalizeLoginId, passwordError } from './authRules.ts'

describe('loginIdError', () => {
  it('대소문자 구분 없이 영문 시작 영문·숫자 5~20자면 통과한다 (숫자는 필수 아님)', () => {
    for (const ok of ['breadfan', 'BreadFan01', 'a1234', 'a'.repeat(20)]) expect(loginIdError(ok), ok).toBeNull()
    expect(normalizeLoginId('BreadFan01')).toBe('breadfan01')
  })

  it('길이, 시작 문자, 허용 문자를 검사한다', () => {
    for (const bad of ['abcd', 'a'.repeat(21), '1bread', '_bread', 'bread fan', 'bread-fan', '빵집사랑이', ''])
      expect(loginIdError(bad), bad).not.toBeNull()
  })

  it('예약어는 대소문자와 상관없이 거부한다', () => {
    expect(loginIdError('Admin')).toBe('사용할 수 없는 아이디예요.')
    expect(loginIdError('bigbread')).toBe('사용할 수 없는 아이디예요.')
  })
})

describe('passwordError', () => {
  it('10~128자이고 영문·숫자·특수문자를 모두 넣어야 한다', () => {
    expect(passwordError('Ab1!Cd2@E', 'user')).toContain('10~128자')
    expect(passwordError('abcdefghij1', 'user')).toContain('영문, 숫자, 특수문자')
    expect(passwordError('abcdefghij!', 'user')).toContain('영문, 숫자, 특수문자')
    expect(passwordError('1234567890!', 'user')).toContain('영문, 숫자, 특수문자')
    expect(passwordError('BreadLover7!x', 'user')).toBeNull()
    expect(passwordError('빵을좋아해요12!!', 'user')).toBeNull()
  })

  it('아이디를 포함하거나 서로 다른 문자가 5개 미만이면 거부한다', () => {
    expect(passwordError('MyBreadFan#2024', 'BreadFan')).toBe('비밀번호에 아이디를 포함할 수 없어요.')
    expect(passwordError('aaaaaaaaa1!', 'zzzzzz')).toBe('서로 다른 문자를 5개 이상 써 주세요.')
  })

  it('각 조건을 따로 확인할 수 있다 (체크리스트용)', () => {
    expect(checkPassword('abc')).toEqual({ length: false, letter: true, digit: false, special: false, distinct: false })
    expect(checkPassword('BreadLover7!x')).toEqual({ length: true, letter: true, digit: true, special: true, distinct: true })
  })
})

describe('emailError / nicknameError', () => {
  it('이메일 형식을 검사한다', () => {
    expect(emailError(' a@example.com ')).toBeNull()
    for (const bad of ['a', 'a@', '@b.com', 'a@b', 'a b@c.com']) expect(emailError(bad), bad).not.toBeNull()
  })

  it('닉네임은 2~20자다', () => {
    expect(nicknameError('빵')).not.toBeNull()
    expect(nicknameError('빵순이')).toBeNull()
    expect(nicknameError('가'.repeat(21))).not.toBeNull()
  })
})
