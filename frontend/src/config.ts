/** 카카오맵 JS 키. 없으면 지도 영역에 안내 문구를 보여준다. */
export const KAKAO_JS_KEY: string = import.meta.env.KAKAO_JS_KEY?.trim() ?? ''

/** 처음 지도를 열 때의 중심(대전시청 부근)과 줌 레벨 */
export const DAEJEON_CENTER = { lat: 36.3504, lng: 127.3845 }
export const DEFAULT_MAP_LEVEL = 8

/** 백엔드 검증 범위와 같다 (대한민국 위경도). */
export const KOREA_BOUNDS = { minLat: 33, maxLat: 39, minLng: 124, maxLng: 132 }
