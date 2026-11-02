import { useCallback, useMemo, useState } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router'
import { fetchAdminSummary } from '../api/admin.ts'
import AuthMenu from '../components/AuthMenu.tsx'
import AuditPanel from '../components/admin/AuditPanel.tsx'
import BakeriesPanel from '../components/admin/BakeriesPanel.tsx'
import BakeryReportsPanel from '../components/admin/BakeryReportsPanel.tsx'
import { AdminContext } from '../components/admin/adminContext.ts'
import ContentReportsPanel from '../components/admin/ContentReportsPanel.tsx'
import DashboardPanel from '../components/admin/DashboardPanel.tsx'
import UsersPanel from '../components/admin/UsersPanel.tsx'
import { useAsync } from '../hooks/useAsync.ts'
import { useAuth } from '../hooks/useAuth.ts'
import './AdminPage.css'

/** 관리자 화면. 로그인한 관리자만 보이고, 실제 권한은 서버가 요청마다 다시 확인한다. */
export default function AdminPage() {
  const auth = useAuth()
  const [loginOpen, setLoginOpen] = useState(false)
  const isAdmin = auth.user?.role === 'ADMIN'
  const summary = useAsync((signal) => (isAdmin ? fetchAdminSummary(signal) : Promise.resolve(null)), String(isAdmin))
  const refresh = summary.reload
  const refreshSummary = useCallback(() => refresh(), [refresh])
  const context = useMemo(() => ({ refreshSummary }), [refreshSummary])

  const authMenu = (
    <AuthMenu
      loading={auth.loading}
      providers={auth.providers}
      options={auth.options}
      user={auth.user}
      error={auth.error}
      onLogin={auth.login}
      onLogout={() => void auth.logout()}
      onAccountDeleted={auth.forgetUser}
      loginOpen={loginOpen}
      onLoginOpenChange={setLoginOpen}
    />
  )

  if (auth.loading) {
    return (
      <div className="admin">
        <p className="summary admin__center">불러오는 중…</p>
      </div>
    )
  }

  if (!isAdmin) {
    return (
      <div className="admin">
        <header className="app__header admin__header">
          <h1>
            <Link to="/" className="auth-page__home">
              대빵 <span aria-hidden="true">🥖</span>
            </Link>{' '}
            <span className="admin__title">관리자</span>
          </h1>
          {authMenu}
        </header>
        <main className="admin__center">
          <section className="auth-card" aria-labelledby="admin-guard">
            {auth.user === null ? (
              <>
                <h2 id="admin-guard">로그인이 필요해요</h2>
                <p>관리자 계정으로 로그인해 주세요.</p>
                <button type="button" className="btn btn--primary" onClick={() => setLoginOpen(true)}>
                  로그인
                </button>
              </>
            ) : (
              <>
                <h2 id="admin-guard">관리자만 들어올 수 있어요</h2>
                <p>이 계정은 관리자 권한이 없어요.</p>
                <Link className="btn" to="/">
                  지도로 돌아가기
                </Link>
              </>
            )}
          </section>
        </main>
      </div>
    )
  }

  const s = summary.data
  const badge = (n: number | undefined) => (n ? <span className="admin__count">{n}</span> : null)
  return (
    <AdminContext.Provider value={context}>
      <div className="admin">
        <header className="app__header admin__header">
          <h1>
            <Link to="/" className="auth-page__home">
              대빵 <span aria-hidden="true">🥖</span>
            </Link>{' '}
            <span className="admin__title">관리자</span>
          </h1>
          {authMenu}
        </header>
        <nav className="admin__nav" aria-label="관리자 메뉴">
          <NavLink to="/admin" end>
            처리할 일
          </NavLink>
          <NavLink to="/admin/bakery-reports">빵집 제보 {badge(s?.pendingBakeryReports)}</NavLink>
          <NavLink to="/admin/content-reports">신고 {badge(s?.pendingContentReports)}</NavLink>
          <NavLink to="/admin/bakeries">빵집 관리</NavLink>
          <NavLink to="/admin/users">사용자</NavLink>
          <NavLink to="/admin/audit">작업 기록</NavLink>
        </nav>
        <main className="admin__main">
          <Routes>
            <Route index element={<DashboardPanel summary={s} error={summary.error} />} />
            <Route path="bakery-reports" element={<BakeryReportsPanel />} />
            <Route path="content-reports" element={<ContentReportsPanel />} />
            <Route path="bakeries" element={<BakeriesPanel />} />
            <Route path="users" element={<UsersPanel />} />
            <Route path="audit" element={<AuditPanel />} />
            <Route path="*" element={<DashboardPanel summary={s} error={summary.error} />} />
          </Routes>
        </main>
      </div>
    </AdminContext.Provider>
  )
}
