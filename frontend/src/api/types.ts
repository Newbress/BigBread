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
