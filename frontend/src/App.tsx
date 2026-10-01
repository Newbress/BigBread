import { useEffect, useState } from 'react'

type District = { id: number; code: string; name: string }

export default function App() {
  const [districts, setDistricts] = useState<District[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/districts')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<District[]>
      })
      .then(setDistricts)
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <main>
      <h1>대빵 🥖</h1>
      <p>대전 빵집 탐색 서비스</p>
      {error && <p role="alert">API 연결 실패: {error}</p>}
      <ul>
        {districts.map((d) => (
          <li key={d.id}>{d.name}</li>
        ))}
      </ul>
    </main>
  )
}
