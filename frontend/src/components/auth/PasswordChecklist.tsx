import { checkPassword, PASSWORD_MAX, PASSWORD_MIN } from '../../lib/authRules.ts'

/** 비밀번호 규칙을 입력하는 동안 하나씩 충족 여부로 보여준다. */
export default function PasswordChecklist({ password }: { password: string }) {
  const checks = checkPassword(password)
  const items: [boolean, string][] = [
    [checks.length, `${PASSWORD_MIN}~${PASSWORD_MAX}자`],
    [checks.letter, '영문 포함'],
    [checks.digit, '숫자 포함'],
    [checks.special, '특수문자 포함'],
    [checks.distinct, '서로 다른 문자 5개 이상'],
  ]
  return (
    <ul className="checklist" aria-label="비밀번호 규칙">
      {items.map(([ok, text]) => (
        <li key={text} className={ok ? 'checklist__item checklist__item--ok' : 'checklist__item'}>
          <span aria-hidden="true">{ok ? '✓' : '○'}</span> {text}
          <span className="visually-hidden">{ok ? ' (충족)' : ' (미충족)'}</span>
        </li>
      ))}
    </ul>
  )
}
