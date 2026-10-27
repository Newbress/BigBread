import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { verifyEmail } from '../api/client.ts'
import AuthPageLayout from '../components/AuthPageLayout.tsx'

type State = 'verifying' | 'success' | 'failed'

const NO_TOKEN = '인증 링크가 올바르지 않아요. 메일의 링크를 그대로 열어 주세요.'

/** 가입 인증 메일의 링크(/verify-email?token=...)가 열리는 화면. */
export default function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [state, setState] = useState<State>(token ? 'verifying' : 'failed')
  const [message, setMessage] = useState(token ? '' : NO_TOKEN)
  // 개발 모드(StrictMode)는 효과를 두 번 실행한다. 토큰은 한 번만 쓸 수 있으므로 요청도 한 번만 보낸다.
  const started = useRef(false)

  useEffect(() => {
    if (!token || started.current) return
    started.current = true
    verifyEmail(token)
      .then(() => setState('success'))
      .catch((e: unknown) => {
        setMessage(e instanceof Error ? e.message : '인증에 실패했어요.')
        setState('failed')
      })
  }, [token])

  return (
    <AuthPageLayout title="이메일 인증">
      {state === 'verifying' && (
        <p className="notice" role="status">
          인증하는 중…
        </p>
      )}
      {state === 'success' && (
        <>
          <p className="notice" role="status">
            가입이 완료됐어요! 이제 아이디로 로그인할 수 있어요.
          </p>
          <Link className="btn btn--on btn--block" to="/?login=1">
            로그인하러 가기
          </Link>
        </>
      )}
      {state === 'failed' && (
        <>
          <p className="notice notice--error" role="alert">
            {message}
          </p>
          <Link className="btn btn--block" to="/">
            지도로 돌아가기
          </Link>
        </>
      )}
    </AuthPageLayout>
  )
}
