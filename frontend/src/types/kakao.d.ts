// 사용하는 카카오맵 JS SDK API만 최소로 선언한다. (https://apis.map.kakao.com/web/documentation/)
declare namespace kakao.maps {
  function load(callback: () => void): void

  class LatLng {
    constructor(lat: number, lng: number)
    getLat(): number
    getLng(): number
  }

  class LatLngBounds {
    constructor()
    extend(latlng: LatLng): void
    isEmpty(): boolean
  }

  interface MapOptions {
    center: LatLng
    level?: number
  }

  class Map {
    constructor(container: HTMLElement, options: MapOptions)
    setCenter(latlng: LatLng): void
    panTo(latlng: LatLng): void
    setLevel(level: number): void
    setBounds(
      bounds: LatLngBounds,
      paddingTop?: number,
      paddingRight?: number,
      paddingBottom?: number,
      paddingLeft?: number,
    ): void
    relayout(): void
  }

  interface CustomOverlayOptions {
    position: LatLng
    content: HTMLElement | string
    xAnchor?: number
    yAnchor?: number
    zIndex?: number
    clickable?: boolean
    map?: Map | null
  }

  class CustomOverlay {
    constructor(options: CustomOverlayOptions)
    setMap(map: Map | null): void
    setZIndex(zIndex: number): void
  }
}

interface Window {
  kakao?: typeof kakao
}
