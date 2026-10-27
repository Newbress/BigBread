import type { ReactNode } from 'react'
import { Link } from 'react-router'

/** 메일 링크로 들어오는 화면(가입 인증, 비밀번호 재설정)의 공통 틀. */
export default function AuthPageLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="auth-page">
      <header className="app__header">
        <h1>
          <Link to="/" className="auth-page__home">
            대빵 <span aria-hidden="true">🥖</span>
          </Link>
        </h1>
      </header>
      <main className="auth-page__main">
        <section className="auth-card" aria-labelledby="auth-page-title">
          <h2 id="auth-page-title">{title}</h2>
          {children}
        </section>
      </main>
    </div>
  )
}
