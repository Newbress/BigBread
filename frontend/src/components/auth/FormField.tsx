import type { ReactNode } from 'react'
import { useId } from 'react'

type Props = {
  label: string
  /** 입력창 아래에 보여줄 도움말 또는 오류 */
  hint?: ReactNode
  error?: string | null
  children: (props: { id: string; describedBy: string | undefined; invalid: boolean }) => ReactNode
}

/** 라벨과 입력창, 도움말/오류 문구를 서로 연결한다 (스크린 리더가 입력창을 읽을 때 오류도 함께 읽는다). */
export default function FormField({ label, hint, error, children }: Props) {
  const id = useId()
  const hintId = `${id}-hint`
  const hasHint = Boolean(error) || Boolean(hint)
  return (
    <div className="field-block">
      <label htmlFor={id}>{label}</label>
      {children({ id, describedBy: hasHint ? hintId : undefined, invalid: Boolean(error) })}
      {hasHint && (
        <p id={hintId} className={error ? 'field-block__hint field-block__hint--error' : 'field-block__hint'}>
          {error ?? hint}
        </p>
      )}
    </div>
  )
}
