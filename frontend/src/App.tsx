import { useMemo, useState } from 'react'
import './App.css'
import type { BakerySort, BakerySummary, RouteResponse, SearchParams } from './api/types.ts'
import AuthMenu from './components/AuthMenu.tsx'
import BakeryDetailPanel from './components/BakeryDetailPanel.tsx'
import BakeryList from './components/BakeryList.tsx'
import CoursesPanel from './components/CoursesPanel.tsx'
import FavoritesPanel from './components/FavoritesPanel.tsx'
import FilterBar from './components/FilterBar.tsx'
import KakaoMap from './components/KakaoMap.tsx'
import RoutePanel from './components/RoutePanel.tsx'
import { KAKAO_JS_KEY } from './config.ts'
import { useBakeries } from './hooks/useBakeries.ts'
import { useAuth } from './hooks/useAuth.ts'
import { useCourses } from './hooks/useCourses.ts'
import { useFavorites } from './hooks/useFavorites.ts'
import { useDistricts } from './hooks/useDistricts.ts'
import { useGeolocation } from './hooks/useGeolocation.ts'
import { useRoute } from './hooks/useRoute.ts'
import { wantsLoginOnLoad } from './lib/loginParam.ts'
import { routeColor } from './lib/route.ts'

const DEFAULT_RADIUS = 3000

type View = 'list' | 'detail' | 'route' | 'favorites' | 'courses'
/** 상세에서 '뒤로'를 눌렀을 때 돌아갈 화면 (= 상세가 열려 있는 탭) */
type TabView = Exclude<View, 'detail'>

export default function App() {
  const { districts, error: districtError } = useDistricts()
  const geo = useGeolocation()
  const auth = useAuth()
  // 소셜 로그인이 실패해 돌아오면 주소에 ?login=error가 붙는다.
  const [loginFailed, setLoginFailed] = useState(() => new URLSearchParams(window.location.search).get('login') === 'error')

  const [district, setDistrict] = useState<string | null>(null)
  const [sort, setSort] = useState<BakerySort>('POPULARITY')
  const [radius, setRadius] = useState<number | null>(DEFAULT_RADIUS)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [view, setView] = useState<View>('list')
  const [returnView, setReturnView] = useState<TabView>('list')
  const [loginOpen, setLoginOpen] = useState(wantsLoginOnLoad)

  const params = useMemo<SearchParams>(
    () => ({ district, location: geo.location, radius: geo.location ? radius : null, sort }),
    [district, geo.location, radius, sort],
  )
  const [listVersion, setListVersion] = useState(0)
  const { data, loading, error } = useBakeries(params, listVersion)
  const items = useMemo(() => data?.items ?? [], [data])
  const route = useRoute(geo.location)
  const favorites = useFavorites(auth.user)
  const courses = useCourses(auth.user)
  const basketIds = useMemo(() => new Set(route.basket.map((bakery) => bakery.id)), [route.basket])

  // 필터가 바뀌어 결과에서 사라진 빵집은 선택을 풀어 준다.
  const activeId = selectedId !== null && items.some((bakery) => bakery.id === selectedId) ? selectedId : null

  // 상세는 목록에 없어도 동선에 담았거나 즐겨찾기한 빵집이면 열 수 있다.
  const detailId =
    selectedId !== null && (activeId !== null || basketIds.has(selectedId) || favorites.ids.has(selectedId))
      ? selectedId
      : null
  const detailSummary =
    detailId === null
      ? undefined
      : (items.find((b) => b.id === detailId) ??
        route.basket.find((b) => b.id === detailId) ??
        favorites.items.find((item) => item.bakery.id === detailId)?.bakery)

  // 상세는 들어온 화면의 탭 아래에 열린다.
  const tab: TabView = view === 'detail' ? returnView : view

  const requireLogin = () => {
    if (auth.user) return true
    setLoginOpen(true)
    return false
  }
  const toggleFavorite = (bakery: BakerySummary) => {
    if (requireLogin()) void favorites.toggle(bakery)
  }
  const favoriteBakeries = useMemo(() => favorites.items.map((item) => item.bakery), [favorites.items])
  const saveCourse = (title: string, result: RouteResponse) =>
    courses.save(title, result.order, result.stops.map((stop) => stop.bakery.id))
  const loadCourse = async (id: number) => {
    route.replaceBasket(await courses.loadStops(id))
    setView('route')
  }

  const routeResult = view === 'route' ? route.route : null
  const mapBakeries = useMemo(
    () => (routeResult ? routeResult.stops.map((stop) => stop.bakery) : tab === 'favorites' ? favoriteBakeries : items),
    [routeResult, tab, favoriteBakeries, items],
  )
  const mapSelectedId = routeResult ? null : tab === 'favorites' ? (selectedId !== null && favorites.ids.has(selectedId) ? selectedId : null) : activeId
  const pinColors = useMemo(() => routeResult?.stops.map((stop) => routeColor(stop.seq)), [routeResult])
  const segments = useMemo(
    () =>
      routeResult?.stops.flatMap((stop) =>
        stop.leg && stop.leg.path.length >= 2
          ? [{ path: stop.leg.path, color: routeColor(stop.seq), estimated: stop.leg.estimated }]
          : [],
      ),
    [routeResult],
  )

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
        <AuthMenu
          loading={auth.loading}
          providers={auth.providers}
          options={auth.options}
          user={auth.user}
          error={auth.error}
          onLogin={auth.login}
          onLogout={auth.logout}
          loginOpen={loginOpen}
          onLoginOpenChange={setLoginOpen}
        />
      </header>
      {loginFailed && (
        <p className="notice notice--error app__banner" role="alert">
          로그인에 실패했어요. 잠시 후 다시 시도해 주세요.{' '}
          <button
            type="button"
            className="btn btn--small"
            onClick={() => {
              setLoginFailed(false)
              window.history.replaceState(null, '', window.location.pathname)
            }}
          >
            닫기
          </button>
        </p>
      )}

      <main className="app__main">
        <aside className="app__side">
          <nav className="tabs" aria-label="보기 전환">
            {(
              [
                ['list', '빵집 목록'],
                ['favorites', auth.user ? `즐겨찾기 (${favorites.items.length})` : '즐겨찾기'],
                ['route', `내 동선 (${route.basket.length})`],
                ['courses', '내 코스'],
              ] as [TabView, string][]
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                className="tabs__tab"
                aria-current={tab === value ? 'page' : undefined}
                onClick={() => setView(value)}
              >
                {label}
              </button>
            ))}
          </nav>

          {favorites.error && tab !== 'favorites' && (
            <p className="notice notice--error" role="alert">
              {favorites.error}
            </p>
          )}

          {tab === 'list' && (
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
          )}

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
              isFavorite={favorites.ids.has(detailId)}
              onToggleFavorite={detailSummary ? () => toggleFavorite(detailSummary) : undefined}
              user={auth.user}
              onLogin={() => setLoginOpen(true)}
              onReviewsChanged={() => setListVersion((n) => n + 1)}
            />
          ) : view === 'favorites' ? (
            <FavoritesPanel
              loggedIn={auth.user !== null}
              onLogin={() => setLoginOpen(true)}
              items={favorites.items}
              loading={favorites.loading}
              error={favorites.error}
              selectedId={selectedId}
              onSelect={handleSelect}
              basketIds={basketIds}
              basketFull={route.full}
              onToggleBasket={route.toggle}
              favoriteIds={favorites.ids}
              onToggleFavorite={toggleFavorite}
            />
          ) : view === 'courses' ? (
            <CoursesPanel
              loggedIn={auth.user !== null}
              onLogin={() => setLoginOpen(true)}
              courses={courses.items}
              loading={courses.loading}
              error={courses.error}
              onLoad={loadCourse}
              onDelete={courses.remove}
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
              loggedIn={auth.user !== null}
              onLogin={() => setLoginOpen(true)}
              onSaveCourse={saveCourse}
              onOpenCourses={() => setView('courses')}
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
              favoriteIds={favorites.ids}
              onToggleFavorite={toggleFavorite}
            />
          )}
        </aside>

        <div className="app__map">
          {KAKAO_JS_KEY ? (
            <KakaoMap
              appKey={KAKAO_JS_KEY}
              bakeries={mapBakeries}
              selectedId={mapSelectedId}
              userLocation={geo.location}
              onSelect={handleSelect}
              segments={segments}
              pinColors={pinColors}
              start={routeResult?.start}
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
