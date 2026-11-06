import { Link } from 'react-router'
import AuthPageLayout from '../components/AuthPageLayout.tsx'
import { privacy, terms } from '../legal/documents.ts'
import { OPERATOR, operatorConfigured } from '../legal/operator.ts'

/** /terms, /privacy: 이용약관·개인정보 처리방침. */
export default function LegalPage({ kind }: { kind: 'terms' | 'privacy' }) {
  const doc = kind === 'terms' ? terms() : privacy()
  const other = kind === 'terms' ? { to: '/privacy', label: '개인정보 처리방침' } : { to: '/terms', label: '이용약관' }
  return (
    <AuthPageLayout title={doc.title} wide>
      {!operatorConfigured() && (
        <p className="notice notice--error" role="alert">
          운영자 정보가 아직 설정되지 않았어요. (frontend/src/legal/operator.ts)
        </p>
      )}
      <p className="legal__meta">시행일 {OPERATOR.effectiveDate}</p>
      <p>{doc.intro}</p>
      {doc.sections.map((section) => (
        <section key={section.heading} className="legal__section">
          <h3>{section.heading}</h3>
          {section.paragraphs?.map((text) => <p key={text}>{text}</p>)}
          {section.items && (
            <ul>
              {section.items.map((item) => <li key={item}>{item}</li>)}
            </ul>
          )}
        </section>
      ))}
      <div className="auth-form__links">
        <Link to={other.to}>{other.label}</Link>
        <Link to="/">← 지도로</Link>
      </div>
    </AuthPageLayout>
  )
}
