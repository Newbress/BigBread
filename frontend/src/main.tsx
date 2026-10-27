import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router'
import './index.css'
import App from './App.tsx'
import ResetPasswordPage from './pages/ResetPasswordPage.tsx'
import VerifyEmailPage from './pages/VerifyEmailPage.tsx'

// 지도 화면은 지금처럼 `/` 한 곳이고, 메일 링크가 여는 화면만 경로를 가진다.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="*" element={<App />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
