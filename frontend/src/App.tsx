import { useMemo, useState } from 'react'
import './App.css'
import type { BakerySort, SearchParams } from './api/types.ts'
import BakeryDetailPanel from './components/BakeryDetailPanel.tsx'
import BakeryList from './components/BakeryList.tsx'
import FilterBar from './components/FilterBar.tsx'
import KakaoMap from './components/KakaoMap.tsx'
import RoutePanel from './components/RoutePanel.tsx'
import { KAKAO_JS_KEY } from './config.ts'
import { useBakeries } from './hooks/useBakeries.ts'
import { useDistricts } from './hooks/useDistricts.ts'
import { useGeolocation } from './hooks/useGeolocation.ts'
import { useRoute } from './hooks/useRoute.ts'

const DEFAULT_RADIUS = 3000

export default function App() {
  const { districts, error: districtError } = useDistricts()
  const geo = useGeolocation()

  const [district, setDistrict] = useState<string | null>(null)
  const [sort, setSort] = useState<BakerySort>('POPULARITY')
  const [radius, setRadius] = useState<number | null>(DEFAULT_RADIUS)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [view, setView] = useState<'list' | 'detail' | 'route'>('list')
  // 상세에서 '뒤로'를 눌렀을 때 돌아갈 화면
  const [returnView, setReturnView] = useState<'list' | 'route'>('list')

  const params = useMemo<SearchParams>(
    () => ({ district, location: geo.location, radius: geo.location ? radius : null, sort }),
    [district, geo.location, radius, sort],
  )
  const { data, loading, error } = useBakeries(params)
  const items = useMemo(() => data?.items ?? [], [data])
  const route = useRoute(geo.location)
  const basketIds = useMemo(() => new Set(route.basket.map((bakery) => bakery.id)), [route.basket])

  // 필터가 바뀌어 결과에서 사라진 빵집은 선택을 풀어 준다.
  const activeId = selectedId !== null && items.some((bakery) => bakery.id === selectedId) ? selectedId : null

  // 상세는 목록에 없어도 동선에 담긴 빵집이면 열 수 있다.
  const detailId = selectedId !== null && (activeId !== null || basketIds.has(selectedId)) ? selectedId : null
  const detailSummary = detailId === null ? undefined : (items.find((b) => b.id === detailId) ?? route.basket.find((b) => b.id === detailId))

  const routeResult = view === 'route' ? route.route : null
  const mapBakeries = useMemo(() => (routeResult ? routeResult.stops.map((stop) => stop.bakery) : items), [routeResult, items])

  const handleSelect = (id: number) => {
    setSelectedId(id)
    if (view !== 'detail') setReturnView(view)
    setView('detail')
  }

  const handleLocate = async () => {
    const coords = await geo.locate()
    if (coords) setSort('DISTANCE')
  }

  const handleClearLocation = () => {
    geo.clear()
    setSort((current) => (current === 'DISTANCE' ? 'POPULARITY' : current))
  }

  return (
    <div className="app">
      <header className="app__header">
        <h1>
          대빵 <span aria-hidden="true">🥖</span>
        </h1>
        <p>대전 빵집 지도</p>
        <button
          type="button"
          className={view === 'route' ? 'btn btn--small btn--on' : 'btn btn--small'}
          aria-pressed={view === 'route'}
          onClick={() => setView(view === 'route' ? 'list' : 'route')}
        >
          내 동선 ({route.basket.length})
        </button>
      </header>

      <main className="app__main">
        <aside className="app__side">
          <FilterBar
            districts={districts}
            district={district}
            onDistrictChange={setDistrict}
            sort={sort}
            onSortChange={setSort}
            hasLocation={geo.location !== null}
            locating={geo.status === 'locating'}
            locationError={geo.error}
            onLocate={handleLocate}
            onClearLocation={handleClearLocation}
            radius={radius}
            onRadiusChange={setRadius}
          />

          {districtError && (
            <p className="notice notice--error" role="alert">
              {districtError}
            </p>
          )}

          {view === 'detail' && detailId !== null ? (
            <BakeryDetailPanel
              id={detailId}
              onBack={() => setView(returnView)}
              inBasket={basketIds.has(detailId)}
              basketFull={route.full}
              onToggleBasket={detailSummary ? () => route.toggle(detailSummary) : undefined}
            />
          ) : view === 'route' ? (
            <RoutePanel
              basket={route.basket}
              order={route.order}
              onOrderChange={route.setOrder}
              hasStart={geo.location !== null}
              route={route.route}
              planning={route.planning}
              error={route.error}
              onPlan={route.plan}
              onRemove={route.remove}
              onClear={route.clear}
              onSelect={handleSelect}
              onBack={() => setView('list')}
            />
          ) : (
            <BakeryList
              items={items}
              total={data?.totalElements ?? 0}
              loading={loading}
              error={error}
              hasLocation={geo.location !== null}
              selectedId={activeId}
              onSelect={handleSelect}
              basketIds={basketIds}
              basketFull={route.full}
              onToggleBasket={route.toggle}
            />
          )}
        </aside>

        <div className="app__map">
          {KAKAO_JS_KEY ? (
            <KakaoMap
              appKey={KAKAO_JS_KEY}
              bakeries={mapBakeries}
              selectedId={routeResult ? null : activeId}
              userLocation={geo.location}
              onSelect={handleSelect}
              path={routeResult?.path}
              pathEstimated={routeResult?.estimated}
            />
          ) : (
            <div className="map map--empty">
              <p className="map__status map__status--error" role="alert">
                지도를 보려면 레포 루트 <code>.env</code>에 <code>KAKAO_JS_KEY</code>를 설정하고 개발 서버(
                <code>npm run dev</code>)를 다시 시작해 주세요.
              </p>
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
