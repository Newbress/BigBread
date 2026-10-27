export type Coords = { lat: number; lng: number }

export type District = { id: number; code: string; name: string }

export type BakerySort = 'POPULARITY' | 'DISTANCE' | 'NAME'

export type BakerySummary = {
  id: number
  name: string
  district: District
  roadAddress: string | null
  lat: number
  lng: number
  popularityScore: number
  signatureBreads: string[]
  /** 기준 위치(lat/lng)를 보냈을 때만 값이 있다. */
  distanceMeters: number | null
}

export type PageResponse<T> = {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type BakeryDetail = {
  id: number
  name: string
  district: District
  roadAddress: string | null
  jibunAddress: string | null
  lat: number
  lng: number
  phone: string | null
  /** { mon: "08:00-21:00", ..., sun: "closed" } 형태의 JSON. 없으면 null */
  openingHours: unknown
  popularityScore: number
  links: { instagram: string | null; kakaoMap: string | null }
  breads: {
    id: number
    name: string
    description: string | null
    price: number | null
    signature: boolean
  }[]
  sourceLinks: {
    id: number
    platform: 'NAVER_BLOG' | 'INSTAGRAM'
    url: string
    title: string | null
    summary: string | null
    authorName: string | null
    publishedAt: string | null
  }[]
}

export type SearchParams = {
  district: string | null
  location: Coords | null
  radius: number | null
  sort: BakerySort
  size?: number
}

export type AuthProvider = {
  id: string
  name: string
  /** 브라우저가 이동할 로그인 시작 주소 */
  loginUrl: string
}

export type User = {
  id: number
  nickname: string
  email: string | null
  profileImageUrl: string | null
  role: 'USER' | 'ADMIN'
}

export type Me = { authenticated: boolean; user: User | null }
export type RouteOrder = 'NEAREST' | 'POPULARITY' | 'MANUAL'

export type RouteLeg = {
  distanceMeters: number
  durationMinutes: number
  /** 이 구간(직전 지점 → 이 빵집)의 카카오맵 도보 길찾기 링크 */
  kakaoWalkUrl: string
  /** 이 구간만의 경로 좌표 (구간마다 색을 달리 그리는 데 쓴다) */
  path: Coords[]
  /** 이 구간이 실제 길찾기가 아니라 직선 추정이면 true */
  estimated: boolean
}

export type RouteStop = {
  seq: number
  bakery: BakerySummary
  /** 출발지가 없는 동선의 첫 정류장은 구간이 없다. */
  leg: RouteLeg | null
}

export type RouteResponse = {
  order: RouteOrder
  start: Coords | null
  stops: RouteStop[]
  totalDistanceMeters: number
  totalDurationMinutes: number
  /** true면 실제 길찾기가 아니라 직선거리 기반 추정이 섞여 있다. */
  estimated: boolean
  /** 지도에 그릴 경로 좌표 (출발지 포함) */
  path: Coords[]
}

export type RouteRequest = {
  bakeryIds: number[]
  start: Coords | null
  order: RouteOrder
}

/** 메일을 보낼 수 있어서 회원가입·아이디/비밀번호 찾기를 쓸 수 있으면 signupEnabled */
export type AuthOptions = { signupEnabled: boolean }

export type SignupRequest = {
  loginId: string
  email: string
  password: string
  nickname: string
  agreedToTerms: boolean
}

export type Message = { message: string }
