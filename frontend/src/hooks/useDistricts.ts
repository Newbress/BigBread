import { useEffect, useState } from 'react'
import { fetchDistricts } from '../api/client.ts'
import type { District } from '../api/types.ts'

/** 구 목록을 한 번 불러온다 (id 순으로 정렬). */
export function useDistricts() {
  const [districts, setDistricts] = useState<District[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetchDistricts(controller.signal)
      .then((list) => setDistricts([...list].sort((a, b) => a.id - b.id)))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return
        setError(e instanceof Error ? e.message : '구 목록을 불러오지 못했어요.')
      })
    return () => controller.abort()
  }, [])

  return { districts, error }
}
