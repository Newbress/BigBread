let loading: Promise<void> | null = null

/**
 * 카카오맵 JS SDK를 한 번만 불러온다. (autoload=false 후 kakao.maps.load로 초기화)
 * 실패하면 다음 호출에서 다시 시도할 수 있도록 캐시를 비운다.
 */
export function loadKakaoMaps(appKey: string): Promise<void> {
  if (loading) return loading

  loading = new Promise<void>((resolve, reject) => {
    if (window.kakao?.maps) {
      window.kakao.maps.load(() => resolve())
      return
    }

    const script = document.createElement('script')
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false`
    script.async = true
    script.onload = () => {
      if (!window.kakao?.maps) {
        loading = null
        reject(new Error('카카오맵 SDK를 초기화하지 못했어요.'))
        return
      }
      window.kakao.maps.load(() => resolve())
    }
    script.onerror = () => {
      loading = null
      script.remove()
      reject(
        new Error(
          '카카오맵 SDK를 불러오지 못했어요. JS 키, 등록한 웹 도메인(http://localhost:5173), 카카오맵 사용 설정을 확인해 주세요.',
        ),
      )
    }
    document.head.appendChild(script)
  })
  return loading
}
