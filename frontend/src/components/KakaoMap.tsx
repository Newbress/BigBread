import { useEffect, useRef, useState } from 'react'
import type { BakerySummary, Coords } from '../api/types.ts'
import { DAEJEON_CENTER, DEFAULT_MAP_LEVEL } from '../config.ts'
import { loadKakaoMaps } from '../lib/kakao.ts'

type Props = {
  appKey: string
  bakeries: BakerySummary[]
  selectedId: number | null
  userLocation: Coords | null
  onSelect: (id: number) => void
}

type Pin = {
  overlay: kakao.maps.CustomOverlay
  element: HTMLElement
  position: kakao.maps.LatLng
}

/** 점이 하나면 그 위치로, 여럿이면 모두 보이도록 지도를 맞춘다. */
function fitTo(map: kakao.maps.Map, points: kakao.maps.LatLng[]) {
  if (points.length === 0) return
  if (points.length === 1) {
    map.setCenter(points[0])
    map.setLevel(4)
    return
  }
  const bounds = new kakao.maps.LatLngBounds()
  points.forEach((point) => bounds.extend(point))
  map.setBounds(bounds, 60, 60, 60, 60)
}

function createPinElement(bakery: BakerySummary, rank: number, onClick: () => void): HTMLElement {
  const element = document.createElement('button')
  element.type = 'button'
  element.className = 'pin'
  element.setAttribute('aria-label', `${rank}. ${bakery.name}`)

  // 텍스트는 textContent로만 넣는다 (수집된 이름이 HTML로 해석되지 않게).
  const number = document.createElement('span')
  number.className = 'pin__num'
  number.textContent = String(rank)
  const name = document.createElement('span')
  name.className = 'pin__name'
  name.textContent = bakery.name

  element.append(number, name)
  element.addEventListener('click', onClick)
  return element
}

export default function KakaoMap({ appKey, bakeries, selectedId, userLocation, onSelect }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<kakao.maps.Map | null>(null)
  const pinsRef = useRef(new Map<number, Pin>())
  const onSelectRef = useRef(onSelect)
  const [ready, setReady] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    onSelectRef.current = onSelect
  }, [onSelect])

  // SDK를 불러온 뒤 지도를 한 번 만든다.
  useEffect(() => {
    let cancelled = false
    loadKakaoMaps(appKey)
      .then(() => {
        if (cancelled || !containerRef.current) return
        mapRef.current = new kakao.maps.Map(containerRef.current, {
          center: new kakao.maps.LatLng(DAEJEON_CENTER.lat, DAEJEON_CENTER.lng),
          level: DEFAULT_MAP_LEVEL,
        })
        setReady(true)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : '지도를 불러오지 못했어요.')
      })
    return () => {
      cancelled = true
      mapRef.current = null
      setReady(false)
    }
  }, [appKey])

  // 컨테이너 크기가 바뀌면(반응형 레이아웃) 지도를 다시 맞춘다.
  useEffect(() => {
    const container = containerRef.current
    if (!ready || !container) return
    const observer = new ResizeObserver(() => mapRef.current?.relayout())
    observer.observe(container)
    return () => observer.disconnect()
  }, [ready])

  // 빵집 마커. 목록이나 내 위치가 바뀌면 다시 만들고 화면을 맞춘다.
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map) return

    const pins = pinsRef.current
    bakeries.forEach((bakery, index) => {
      const position = new kakao.maps.LatLng(bakery.lat, bakery.lng)
      const element = createPinElement(bakery, index + 1, () => onSelectRef.current(bakery.id))
      const overlay = new kakao.maps.CustomOverlay({ position, content: element, yAnchor: 1, clickable: true, map })
      pins.set(bakery.id, { overlay, element, position })
    })

    const points = bakeries.map((bakery) => new kakao.maps.LatLng(bakery.lat, bakery.lng))
    if (userLocation) points.push(new kakao.maps.LatLng(userLocation.lat, userLocation.lng))
    fitTo(map, points)

    return () => {
      pins.forEach((pin) => pin.overlay.setMap(null))
      pins.clear()
    }
  }, [ready, bakeries, userLocation])

  // 선택된 마커 강조
  useEffect(() => {
    pinsRef.current.forEach((pin, id) => {
      const selected = id === selectedId
      pin.element.classList.toggle('pin--selected', selected)
      pin.overlay.setZIndex(selected ? 10 : 1)
    })
  }, [ready, bakeries, selectedId])

  // 선택이 바뀌었을 때만 그 마커로 이동 (목록이 바뀔 때는 fitTo가 화면을 정한다)
  useEffect(() => {
    if (!ready || selectedId == null) return
    const pin = pinsRef.current.get(selectedId)
    if (pin) mapRef.current?.panTo(pin.position)
  }, [ready, selectedId])

  // 내 위치 표시
  useEffect(() => {
    const map = mapRef.current
    if (!ready || !map || !userLocation) return

    const dot = document.createElement('div')
    dot.className = 'me-dot'
    dot.setAttribute('role', 'img')
    dot.setAttribute('aria-label', '내 위치')
    const overlay = new kakao.maps.CustomOverlay({
      position: new kakao.maps.LatLng(userLocation.lat, userLocation.lng),
      content: dot,
      xAnchor: 0.5,
      yAnchor: 0.5,
      zIndex: 0,
      map,
    })
    return () => overlay.setMap(null)
  }, [ready, userLocation])

  return (
    <div className="map">
      <div ref={containerRef} className="map__canvas" />
      {!ready && !error && <p className="map__status">지도를 불러오는 중…</p>}
      {error && (
        <p className="map__status map__status--error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
