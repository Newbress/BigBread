import { useCallback, useState } from 'react'
import type { Coords } from '../api/types.ts'
import { KOREA_BOUNDS } from '../config.ts'

type Status = 'idle' | 'locating' | 'error'

export function isInKorea({ lat, lng }: Coords): boolean {
  const b = KOREA_BOUNDS
  return lat >= b.minLat && lat <= b.maxLat && lng >= b.minLng && lng <= b.maxLng
}

function messageFor(error: GeolocationPositionError): string {
  switch (error.code) {
    case error.PERMISSION_DENIED:
      return '위치 권한이 꺼져 있어요. 브라우저 주소창의 사이트 설정에서 위치를 허용해 주세요.'
    case error.POSITION_UNAVAILABLE:
      return '현재 위치를 알 수 없어요. 잠시 후 다시 시도해 주세요.'
    case error.TIMEOUT:
      return '위치를 가져오는 데 너무 오래 걸려요. 다시 시도해 주세요.'
    default:
      return '위치를 가져오지 못했어요.'
  }
}

/** 브라우저 Geolocation으로 내 위치를 가져온다. (HTTPS 또는 localhost에서만 동작) */
export function useGeolocation() {
  const [location, setLocation] = useState<Coords | null>(null)
  const [status, setStatus] = useState<Status>('idle')
  const [error, setError] = useState<string | null>(null)

  const fail = useCallback((message: string) => {
    setStatus('error')
    setError(message)
    return null
  }, [])

  /** 성공하면 좌표, 실패하면 null을 돌려준다 (실패 이유는 error에 담긴다). */
  const locate = useCallback((): Promise<Coords | null> => {
    if (!('geolocation' in navigator)) {
      return Promise.resolve(fail('이 브라우저는 위치 정보를 지원하지 않아요.'))
    }
    setStatus('locating')
    setError(null)

    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const coords = { lat: position.coords.latitude, lng: position.coords.longitude }
          if (!isInKorea(coords)) {
            resolve(fail('대한민국 안의 위치만 지원해요.'))
            return
          }
          setLocation(coords)
          setStatus('idle')
          resolve(coords)
        },
        (positionError) => resolve(fail(messageFor(positionError))),
        { enableHighAccuracy: false, timeout: 10_000, maximumAge: 60_000 },
      )
    })
  }, [fail])

  const clear = useCallback(() => {
    setLocation(null)
    setStatus('idle')
    setError(null)
  }, [])

  return { location, status, error, locate, clear }
}
