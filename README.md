# 대빵 (BigBread) 🥖

대전 5개 구(동구·중구·서구·유성구·대덕구)의 유명 빵집을 지도에 표시하고, 대표 빵·원본 리뷰 링크·도보 동선을 제공하는 웹 서비스.

## 구조

| 모듈 | 설명 |
|---|---|
| `core` | JPA 엔티티/리포지토리, Flyway 마이그레이션 (`db/migration`) |
| `api` | REST API (Spring Boot, 포트 8080). 스키마 마이그레이션 담당 |
| `collector` | 데이터 수집기 (카카오 로컬 / 네이버 블로그 / Meta Graph API). 추후 별도 프로세스로 분리 가능 |
| `frontend` | React + Vite + TypeScript (포트 5173, `/api`는 8080으로 프록시) |

## 기술 스택

Java 21 · Spring Boot 4.1 · Gradle · PostgreSQL 16 + PostGIS · Flyway · React 19 · Kakao Maps

## 사전 준비

- Docker Desktop (PostGIS 컨테이너용)
- Node.js 22+
- JDK 21은 없어도 된다: 첫 `./gradlew` 실행 때 Gradle이 자동으로 내려받는다(인터넷 필요). 직접 설치하려면 Windows에서 `winget install EclipseAdoptium.Temurin.21.JDK`

## 실행

```bash
cp .env.example .env          # 키 값 채우기 (커밋 금지)
docker compose up -d db       # PostgreSQL + PostGIS
./gradlew :api:bootRun        # http://localhost:8080/api/districts
cd frontend && npm install && npm run dev
```

> **포트 충돌**: PC에 PostgreSQL이 이미 설치돼 5432를 쓰고 있으면 `localhost:5432` 접속이 컨테이너가 아니라 그 DB로 가서
> `password authentication failed`가 난다. `.env`에 `DB_PORT=5433`을 넣고 `docker compose up -d db`로 다시 띄우면 된다.

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/districts` | 대전 5개 구 목록 |
| GET | `/api/bakeries` | 빵집 목록 (구 필터, 반경 검색, 정렬, 페이지네이션) |
| GET | `/api/bakeries/{id}` | 빵집 상세 (대표 빵, 원본 게시물 링크, 영업시간, 인스타·카카오맵 링크) |

`/api/bakeries` 쿼리 파라미터

| 이름 | 설명 | 기본값 |
|---|---|---|
| `district` | 구 코드 `DONG` `JUNG` `SEO` `YUSEONG` `DAEDEOK` | 전체 |
| `lat`, `lng` | 내 위치(함께 지정). 지정하면 응답에 `distanceMeters`가 포함됨 | - |
| `radius` | 반경(m, 최대 20000). `lat`/`lng` 필요 | 제한 없음 |
| `sort` | `POPULARITY`(인기순) `DISTANCE`(가까운 순, `lat`/`lng` 필요) `NAME` | `POPULARITY` |
| `page`, `size` | 페이지(0부터), 크기(1~50) | 0, 20 |

```bash
curl "localhost:8080/api/bakeries?district=YUSEONG"
curl "localhost:8080/api/bakeries?lat=36.3283&lng=127.4280&radius=1500&sort=DISTANCE"
```

잘못된 요청은 400, 없거나 숨김 처리된 빵집은 404를 [ProblemDetail](https://www.rfc-editor.org/rfc/rfc9457) 형식으로 반환한다.

### 개발용 샘플 데이터

화면/API를 확인하려면 `dev` 프로필로 실행한다. 가상의 `[샘플]` 빵집 10곳(5개 구)이 들어간다 (실제 가게 정보 아님).

```bash
./gradlew :api:bootRun --args="--spring.profiles.active=dev"
```

## 테스트

`api` 통합 테스트는 실제 PostGIS DB가 필요하다. 기본값은 `localhost:5432/bigbread`이며
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 변경할 수 있다.

```bash
./gradlew build
```

## 브랜치 / 커밋 규칙

| 브랜치 | 용도 |
|---|---|
| `main` | 운영(배포) 브랜치. `dev`에서 검증된 변경만 PR로 반영 |
| `dev` | 개발 통합 브랜치. 모든 작업 브랜치는 여기로 PR |
| `feat/…`, `fix/…`, `chore/…`, `docs/…` | 작업 브랜치. `dev`에서 분기 (예: `feat/bakery-api`) |

흐름: `feat/*` → PR → `dev` → (릴리스) PR → `main`

- 커밋: `type: 설명` (Conventional Commits)
