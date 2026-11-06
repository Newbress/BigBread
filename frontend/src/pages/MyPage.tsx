import { useState } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router'
import AccountSettings from '../components/account/AccountSettings.tsx'
import AuthMenu from '../components/AuthMenu.tsx'
import MyCoursesPanel from '../components/my/MyCoursesPanel.tsx'
import { MyBakeryReportsPanel, MyContentReportsPanel } from '../components/my/MyReportsPanel.tsx'
import MyReviewsPanel from '../components/my/MyReviewsPanel.tsx'
import { useAuth } from '../hooks/useAuth.ts'
import './AdminPage.css'
import './MyPage.css'

/** 내 계정: 내 후기, 내 코스, 내 제보, 내 신고, 계정 설정(비밀번호 변경·탈퇴). */
export default function MyPage() {
  const auth = useAuth()
  const [loginOpen, setLoginOpen] = useState(false)
  const [deleted, setDeleted] = useState(false)

  const header = (
    <header className="app__header admin__header">
      <h1>
        <Link to="/" className="auth-page__home">
          대빵 <span aria-hidden="true">🥖</span>
        </Link>{' '}
        <span className="admin__title">내 계정</span>
      </h1>
      <AuthMenu
        loading={auth.loading}
        providers={auth.providers}
        options={auth.options}
        user={auth.user}
        error={auth.error}
        onLogin={auth.login}
        onLogout={() => void auth.logout()}
        loginOpen={loginOpen}
        onLoginOpenChange={setLoginOpen}
      />
    </header>
  )

  if (auth.loading) {
    return (
      <div className="admin">
        <p className="summary admin__center">불러오는 중…</p>
      </div>
    )
  }

  if (deleted) {
    return (
      <div className="admin">
        {header}
        <main className="admin__center">
          <section className="auth-card">
            <h2>탈퇴가 끝났어요</h2>
            <p>그동안 이용해 주셔서 감사합니다.</p>
            <Link className="btn btn--primary" to="/">
              지도로 가기
            </Link>
          </section>
        </main>
      </div>
    )
  }

  if (auth.user === null) {
    return (
      <div className="admin">
        {header}
        <main className="admin__center">
          <section className="auth-card" aria-labelledby="my-guard">
            <h2 id="my-guard">로그인이 필요해요</h2>
            <p>로그인하면 내 후기, 코스, 제보, 신고 내역을 볼 수 있어요.</p>
            <button type="button" className="btn btn--primary" onClick={() => setLoginOpen(true)}>
              로그인
            </button>
          </section>
        </main>
      </div>
    )
  }

  return (
    <div className="admin">
      {header}
      <nav className="admin__nav" aria-label="내 계정 메뉴">
        <NavLink to="/my" end>
          내 후기
        </NavLink>
        <NavLink to="/my/courses">내 코스</NavLink>
        <NavLink to="/my/bakery-reports">내 제보</NavLink>
        <NavLink to="/my/reports">내 신고</NavLink>
        <NavLink to="/my/account">계정 설정</NavLink>
      </nav>
      <main className="admin__main">
        <Routes>
          <Route index element={<MyReviewsPanel />} />
          <Route path="courses" element={<MyCoursesPanel />} />
          <Route path="bakery-reports" element={<MyBakeryReportsPanel />} />
          <Route path="reports" element={<MyContentReportsPanel />} />
          <Route
            path="account"
            element={
              <AccountSettings
                nickname={auth.user.nickname}
                onDeleted={() => {
                  auth.forgetUser()
                  setDeleted(true)
                }}
              />
            }
          />
          <Route path="*" element={<MyReviewsPanel />} />
        </Routes>
      </main>
    </div>
  )
}
