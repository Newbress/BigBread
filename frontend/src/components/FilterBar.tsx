import type { BakerySort, District } from '../api/types.ts'

const RADIUS_OPTIONS = [500, 1000, 2000, 3000, 5000]

function radiusLabel(meters: number): string {
  return meters < 1000 ? `${meters}m` : `${meters / 1000}km`
}

type Props = {
  districts: District[]
  district: string | null
  onDistrictChange: (code: string | null) => void
  sort: BakerySort
  onSortChange: (sort: BakerySort) => void
  hasLocation: boolean
  locating: boolean
  locationError: string | null
  onLocate: () => void
  onClearLocation: () => void
  radius: number | null
  onRadiusChange: (radius: number | null) => void
}

export default function FilterBar({
  districts,
  district,
  onDistrictChange,
  sort,
  onSortChange,
  hasLocation,
  locating,
  locationError,
  onLocate,
  onClearLocation,
  radius,
  onRadiusChange,
}: Props) {
  return (
    <section className="filters" aria-label="빵집 필터">
      <div className="chips" role="group" aria-label="구 선택">
        <button type="button" className="chip" aria-pressed={district === null} onClick={() => onDistrictChange(null)}>
          전체
        </button>
        {districts.map((d) => (
          <button
            key={d.id}
            type="button"
            className="chip"
            aria-pressed={district === d.code}
            onClick={() => onDistrictChange(d.code)}
          >
            {d.name}
          </button>
        ))}
      </div>

      <div className="toolbar">
        {hasLocation ? (
          <button type="button" className="btn btn--on" onClick={onClearLocation}>
            내 위치 끄기
          </button>
        ) : (
          <button type="button" className="btn" onClick={onLocate} disabled={locating}>
            {locating ? '위치 찾는 중…' : '내 위치로 찾기'}
          </button>
        )}

        <label className="field">
          <span>반경</span>
          <select
            value={radius === null ? 'ALL' : String(radius)}
            disabled={!hasLocation}
            onChange={(e) => onRadiusChange(e.target.value === 'ALL' ? null : Number(e.target.value))}
          >
            {RADIUS_OPTIONS.map((meters) => (
              <option key={meters} value={meters}>
                {radiusLabel(meters)}
              </option>
            ))}
            <option value="ALL">제한 없음</option>
          </select>
        </label>

        <label className="field">
          <span>정렬</span>
          <select value={sort} onChange={(e) => onSortChange(e.target.value as BakerySort)}>
            <option value="POPULARITY">인기순</option>
            <option value="DISTANCE" disabled={!hasLocation}>
              가까운 순
            </option>
            <option value="NAME">이름순</option>
          </select>
        </label>
      </div>

      {locationError && (
        <p className="notice notice--error" role="alert">
          {locationError}
        </p>
      )}
    </section>
  )
}
