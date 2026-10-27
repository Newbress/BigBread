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
| GET | `/api/auth/providers` | 로그인 가능한 소셜 제공자 (키가 설정된 것만) |
| GET | `/api/auth/id-available?loginId=` | 자체 가입 아이디 중복 확인 |
| POST | `/api/auth/signup` | 자체 가입 요청 (인증 메일 발송) |
| POST | `/api/auth/verify-email` | 메일 링크의 토큰으로 가입 완료 |
| POST | `/api/auth/login` | 자체 로그인 (아이디/비밀번호) |
| POST | `/api/auth/password/forgot` | 비밀번호 재설정 링크를 메일로 요청 (아이디 입력) |
| POST | `/api/auth/password/reset` | 링크의 토큰과 새 비밀번호로 재설정 |
| POST | `/api/auth/find-id` | 이메일로 가입된 아이디를 메일로 안내 |
| GET | `/api/me` | 로그인 상태와 내 정보 (비로그인이면 `authenticated: false`) |
| POST | `/api/logout` | 로그아웃 (CSRF 토큰 필요) |
| GET | `/api/bakeries/{id}` | 빵집 상세 (대표 빵, 원본 게시물 링크, 영업시간, 인스타·카카오맵 링크) |
| POST | `/api/routes` | 선택한 빵집들의 도보 동선 (방문 순서, 구간별 거리·시간, 카카오맵 길찾기 링크) |

`/api/bakeries` 쿼리 파라미터

| 이름 | 설명 | 기본값 |
|---|---|---|
| `district` | 구 코드 `DONG` `JUNG` `SEO` `YUSEONG` `DAEDEOK` | 전체 |
| `lat`, `lng` | 내 위치(함께 지정). 지정하면 응답에 `distanceMeters`가 포함됨 | - |
| `radius` | 반경(m, 최대 20000). `lat`/`lng` 필요 | 제한 없음 |
| `sort` | `POPULARITY`(인기순) `DISTANCE`(가까운 순, `lat`/`lng` 필요) `NAME`(가나다순). 아래 동점 처리 참고 | `POPULARITY` |
| `page`, `size` | 페이지(0부터), 크기(1~50) | 0, 20 |

```bash
curl "localhost:8080/api/bakeries?district=YUSEONG"
curl "localhost:8080/api/bakeries?lat=36.3283&lng=127.4280&radius=1500&sort=DISTANCE"
```

정렬 기준이 같을 때는 2차·3차 기준으로 순서를 고정한다 (마지막은 항상 `id`).

| 정렬 | 1차 | 2차 | 3차 |
|---|---|---|---|
| `POPULARITY` | 인기 점수 높은 순 | 가까운 순 (위치가 있을 때) | 이름순 |
| `DISTANCE` | 거리 (10m 단위로 묶음) | 인기 점수 높은 순 | 이름순 |
| `NAME` | 가나다순 | 인기 점수 높은 순 | - |

이름순은 DB 기본 정렬 규칙에 기대지 않고 코드포인트 순(`COLLATE "C"`)으로 고정했다. 한글 완성형은 이 순서가 곧 가나다순이고, 영문은 대소문자를 무시한다.

잘못된 요청은 400, 없거나 숨김 처리된 빵집은 404를 [ProblemDetail](https://www.rfc-editor.org/rfc/rfc9457) 형식으로 반환한다.

### 도보 동선 `POST /api/routes`

| 필드 | 설명 |
|---|---|
| `bakeryIds` | 방문할 빵집 ID (1~10개, 중복 불가) |
| `start` | 출발 위치 `{lat, lng}` (선택). 없으면 첫 빵집이 출발점 |
| `order` | `NEAREST`(기본, 출발지 기준 **최단 동선**) / `POPULARITY`(인기순, 화면에서는 쓰지 않음) / `MANUAL`(요청 순서 그대로) |

```bash
curl -X POST localhost:8080/api/routes -H 'Content-Type: application/json' \
  -d '{"bakeryIds":[1,2,3],"start":{"lat":36.3283,"lng":127.4280},"order":"NEAREST"}'
```

응답에는 방문 순서대로 `stops[]`(빵집 요약 + 직전 지점에서의 `leg`: 거리 m, 소요 분, `kakaoWalkUrl`, 구간 경로 `path`, 직선 추정 여부 `estimated`), 총 거리·시간,
지도에 그릴 `path[]` 좌표가 들어간다.

- `NEAREST`는 Held-Karp 동적 계획법으로 구한 **정확한 최단 경로**다 (출발지로 돌아오지 않는 열린 경로, 최대 10곳).
- 구간마다 [카카오맵 도보 경로 조회](https://developers.kakao.com/docs/ko/kakaomap/rest-api)(`GET /v2/routing/walk`)를 호출해
  **실제 길을 따라가는 거리·소요 시간·경로선**을 구한다. 서버의 `KAKAO_REST_API_KEY`로 인증하며, 앱에서 **카카오맵 사용 설정**이 켜져 있어야 한다.
- 키가 없거나 조회가 실패한 구간은 직선거리 기반 추정(시속 4km, 우회 계수 1.3 가정)으로 대신하고, 그 경우 응답의 `estimated`가 `true`다.
- 최단 동선(`NEAREST`)의 방문 순서는 직선거리로 정한 뒤, 정해진 순서의 구간에만 실제 경로를 조회한다. 동선 한 번에 최대 10번 호출한다.
- `kakaoWalkUrl`은 구간(출발 → 도착) 단위 카카오맵 도보 길찾기 링크다.

### 개발용 샘플 데이터

화면/API를 확인하려면 `dev` 프로필로 실행한다. 가상의 `[샘플]` 빵집 10곳(5개 구)이 들어간다 (실제 가게 정보 아님).

```bash
./gradlew :api:bootRun --args="--spring.profiles.active=dev"
```

## 프론트엔드 (지도 화면)

카카오맵 위에 빵집 마커를 올리고, 구 필터와 내 위치 기반 목록(반경·거리순)을 API에 연결한다.

1. 레포 루트 `.env`에 카카오 **JavaScript 키**를 넣는다: `KAKAO_JS_KEY=...`
2. [Kakao Developers](https://developers.kakao.com) → 내 애플리케이션에서 설정한다.
   - 앱 설정 → 플랫폼 → **Web** → 사이트 도메인에 `http://localhost:5173` 등록 (배포 시 운영 도메인도 추가)
   - 제품 설정 → **카카오맵**을 사용 설정 ON
3. 백엔드를 `dev` 프로필로 띄우고(샘플 데이터) 프론트엔드를 실행한다.

```bash
./gradlew :api:bootRun --args="--spring.profiles.active=dev"
cd frontend && npm install && npm run dev      # http://localhost:5173
```

- Vite가 루트 `.env`를 읽되, 브라우저에는 `VITE_`와 `KAKAO_JS_` 접두사 변수만 노출한다. `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET` 등은 노출되지 않는다.
- `.env`를 고친 뒤에는 `npm run dev`를 다시 시작해야 반영된다.
- 지도가 안 뜨면 화면의 안내 문구를 확인한다: 키 없음 / 도메인 미등록 / 카카오맵 사용 설정 OFF가 흔한 원인이다.
- 내 위치(Geolocation)는 HTTPS 또는 `localhost`에서만 동작한다.

## 소셜 로그인 (카카오 · 네이버 · 구글)

서버 세션(HttpOnly 쿠키) 방식의 OAuth2 인가 코드 흐름이다. 키가 설정된 제공자만 켜지고, 하나도 없으면 로그인 버튼이 나오지 않는다(지도와 목록은 그대로 동작).

### 설정
`.env`에 제공자별 키를 넣는다 (`.env.example` 참고). 키 값은 커밋하지 않는다.

| 제공자 | 환경 변수 | 콘솔에서 할 일 (화면 구성은 바뀔 수 있다) |
|---|---|---|
| 카카오 | `KAKAO_REST_API_KEY`(클라이언트 ID), `KAKAO_CLIENT_SECRET` | 앱 → **카카오 로그인** 사용 설정 ON → Redirect URI 등록 → 동의항목에서 **닉네임, 프로필 사진** 설정. 보안에서 Client Secret을 켰다면 그 값을 `KAKAO_CLIENT_SECRET`에 |
| 네이버 | `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | 애플리케이션 등록 → 사용 API **네이버 로그인**, 제공 정보(별명, 프로필 사진 등) 선택 → 서비스 URL과 Callback URL 등록. 개발 중 상태에서는 **멤버 관리**에 넣은 계정만 로그인할 수 있다 |
| 구글 | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google Cloud Console → OAuth 동의 화면(테스트 사용자 추가) → **OAuth 클라이언트 ID(웹 애플리케이션)** 생성 → 승인된 리디렉션 URI 등록 |

콘솔에 등록하는 **리다이렉트 URI**는 `APP_BASE_URL`(개발: `http://localhost:5173`) 뒤에 아래를 붙인 값이다. 프론트 개발 서버가 `/oauth2`, `/login/oauth2`를 백엔드로 넘겨 주므로 항상 5173 주소로 등록한다.

```
http://localhost:5173/login/oauth2/code/kakao
http://localhost:5173/login/oauth2/code/naver
http://localhost:5173/login/oauth2/code/google
```

배포할 때는 `APP_BASE_URL`을 운영 도메인으로, `SESSION_COOKIE_SECURE=true`로 바꾸고 콘솔의 리다이렉트 URI도 운영 도메인 것으로 추가한다.

### 동작 방식
- 로그인 시작: 브라우저가 `/oauth2/authorization/{kakao|naver|google}`로 이동 → 제공자 로그인 → `/login/oauth2/code/{id}` 콜백 → 서비스 사용자(`app_user`)를 찾거나 새로 만들고 세션 시작 → `/`로 돌아온다. 실패하면 `/?login=error`로 돌아온다.
- 같은 이메일이어도 **제공자가 다르면 다른 계정**으로 본다 (제공자가 준 이메일을 믿고 자동으로 합치면 계정 탈취에 쓰일 수 있어서).
- 세션에는 사용자 ID와 역할만 저장한다. 제공자의 토큰과 원본 응답은 보관하지 않는다.
- 조회 API는 모두 공개이고, 로그인이 필요한 API는 `/api/my/**` 아래에 둔다 (비로그인이면 401).
- 변경 요청(POST/PUT/DELETE)은 CSRF 토큰이 필요하다: 쿠키 `XSRF-TOKEN` 값을 헤더 `X-XSRF-TOKEN`으로 보낸다. `POST /api/routes`는 저장 없는 계산이라 제외된다.
### 도보 동선 화면

1. 목록이나 상세에서 **담기**를 눌러 빵집을 담는다 (최대 10곳, 담으면 **담음**으로 바뀐다). 사이드 패널 위쪽 **내 동선 (n)** 탭에서 확인한다.
2. 방문 순서(최단 동선 / 인기순 / 담은 순서)를 고르고 **동선 만들기**를 누른다. **내 위치**를 켜 두면 내 위치에서 출발한다.
3. 결과: 방문 순서와 번호, 구간별 거리·도보 시간, 구간별 **카카오맵 길찾기** 링크, 총 거리·시간. 지도에는 번호 마커와 경로선이 그려진다.
   - N번 빵집의 마커, 그곳으로 가는 선, 목록의 번호 배지는 같은 색이다 (1번 갈색, 2번 초록, …). 선이 겹쳐도 어느 쪽이 먼저인지 알 수 있다.
   - 출발 위치에는 **출발** 깃발이 뜨고, 지도 우측 상단의 **동선 전체 보기** / **내 위치로** 버튼으로 시점을 되돌릴 수 있다.
   - 방문 순서는 **최단 동선**과 **담은 순서** 중에서 고른다.
   - 도보 시간은 카카오맵 도보 경로 기준이며 빵집에서 머무는 시간은 포함하지 않는다.
   - 실제 길을 따라가는 선은 **실선**, 길찾기에 실패해 직선으로 추정한 구간이 섞이면 **점선**과 "일부 직선 기준" 표시가 나온다.
   - 담은 빵집·순서·출발지가 바뀌면 이전 결과는 숨겨지고, 다시 만들어야 한다.

## 자체 로그인 (아이디/비밀번호)

소셜 로그인과 같은 세션을 쓴다 (`/api/me`, 로그아웃 동일). 변경 요청은 모두 CSRF 토큰이 필요하다.

| 항목 | 규칙 |
|---|---|
| 아이디 | 영문으로 시작하는 영문·숫자 5~20자, **대소문자 구분 없음**(소문자로 저장), 숫자는 필수 아님. `admin` 등 예약어 금지 |
| 비밀번호 | 10~128자, 영문·숫자·특수문자 각 1개 이상, 서로 다른 문자 5개 이상. 아이디를 포함하거나 흔한 단어(`Password1234!` 등)면 거부 |
| 이메일 | 가입 인증과 (이후) 비밀번호 재설정·아이디 찾기에 쓰는 복구 수단. 소문자로 저장 |

흐름: `signup` → 인증 메일의 링크(`{APP_BASE_URL}/verify-email?token=...`, **24시간**, 1회용) → `verify-email` → 계정 생성 → `login`.

- **인증이 끝나야 계정이 만들어진다.** 가입 요청은 `pending_signup`에만 저장되고, 링크로 인증하면 `app_user` + `local_credential`이 생긴다 (남의 이메일·아이디를 미리 점유하는 계정이 없다).
- 이미 가입된 이메일로 가입을 시도해도 **응답은 똑같고**, 그 이메일 주인에게만 "이미 가입돼 있어요" 메일이 간다. 로그인 실패 메시지도 아이디 없음/비밀번호 틀림을 구분하지 않는다.
- 비밀번호는 Argon2id로 해시해 저장하고(`{argon2}` 접두사로 알고리즘 교체 가능), 인증 토큰은 SHA-256 해시만 저장한다. 로그인하면 세션 ID를 새로 발급한다.
- **메일 발송**: 개발(`dev` 프로필)에서는 인증 링크를 **서버 로그에 출력**한다. 그 외 환경은 발송 수단이 없어 회원가입이 `503`으로 막힌다 (운영 SMTP는 `AuthMailer` 구현체를 추가하면 켜진다).
- 아직 없는 것: 비밀번호 변경·회원 탈퇴(4단계).

### 화면
헤더의 **로그인** 버튼 → 로그인 창에서 쓴다. (메일을 보낼 수 있는 환경이면 가입·찾기가 켜진다: `GET /api/auth/options`의 `signupEnabled`)

| 화면 | 내용 |
|---|---|
| 로그인 창 | 소셜 로그인 버튼(켜진 제공자만) + 아이디/비밀번호 폼, 회원가입 · 아이디 찾기 · 비밀번호 찾기 링크 |
| 회원가입 | 아이디(입력을 벗어나면 중복 확인), 이메일, 비밀번호(규칙 체크리스트), 비밀번호 확인, 닉네임, 개인정보 수집·이용 동의 → 인증 메일 안내 |
| `/verify-email?token=...` | 인증 메일의 링크가 여는 화면: 가입 완료 → "로그인하러 가기"(`/?login=1`로 로그인 창을 바로 연다) |
| `/reset-password?token=...` | 재설정 메일의 링크가 여는 화면: 새 비밀번호 + 확인 → 변경 완료 |

- 입력 규칙은 서버와 같은 규칙으로 입력하는 동안 바로 알려주고, 서버만 아는 규칙(흔한 비밀번호 등)은 서버 응답 메시지로 보여준다.
- 라우터(`react-router`)는 **최소한만** 쓴다: 지도 화면은 `/` 한 곳이고, 메일 링크가 여는 두 화면만 경로를 가진다. 배포할 때는 서버가 모르는 경로를 `index.html`로 돌려주도록(SPA fallback) 설정해야 한다.
- 개인정보 수집·이용 안내 문구는 **임시 초안**이다. 수집 항목·목적·보유 기간은 실제 서비스 정책에 맞게 정리하고, 공개 전에 법적 검토를 받아야 한다.
- 개발에서는 인증·재설정 링크가 **서버 로그**에 출력된다(`[개발용 메일] ...`). 로그의 링크를 브라우저로 열면 된다.

### 비밀번호 재설정 · 아이디 찾기
- `password/forgot`에 아이디를 보내면 그 계정의 **인증된 이메일**로 재설정 링크(`{APP_BASE_URL}/reset-password?token=...`, **30분**, 1회용)를 보낸다. 새로 요청하면 이전 링크는 무효다.
- `password/reset`: 새 비밀번호가 규칙에 맞지 않으면 400이고 링크는 그대로 쓸 수 있다. 성공하면 **그 사용자의 모든 로그인 세션을 끊고**, 실패 횟수·잠금을 풀고, "비밀번호가 변경됐어요" 알림 메일을 보낸다.
- `find-id`: 이메일로 가입된 계정이 있으면 아이디를 메일로 알려준다.
- 두 기능 모두 **계정이 있든 없든 응답이 똑같다** (없으면 아무 메일도 가지 않는다). 메일 발송이 실패해도 응답은 같다.

### 무차별 대입 방어
- **계정 잠금**: 비밀번호를 연속 5번 틀리면 15분 동안 잠기고(`429`), 잠긴 동안은 맞는 비밀번호도 거부한다. 성공하거나 비밀번호를 재설정하면 풀린다. DB에 저장되어 서버를 재시작해도 유지된다.
- **IP별 요청 제한**(초과 시 `429`): 로그인 5분에 30번, 가입 1시간에 10번, 재설정/아이디 찾기 요청 1시간에 5번, 아이디 중복 확인 1분에 60번 등.
- **대상별 제한**: 같은 아이디/이메일로 재설정·아이디 찾기 메일이 1시간에 3통을 넘으면 조용히 보내지 않는다 (응답은 그대로).
- 제한 횟수는 서버 메모리에서 센다 (재시작하면 초기화, 서버가 여러 대면 서버마다 따로). 프록시 뒤에서는 `FORWARD_HEADERS_STRATEGY=framework`를 켜야 접속자 IP로 센다.

### 로그인 세션 (PostgreSQL 저장)
세션은 `spring_session` 테이블에 저장된다(`V3` 마이그레이션). 서버를 재시작·재배포해도 로그인이 유지되고, 사용자 단위로 세션을 끊을 수 있다(비밀번호 재설정 시 사용). 유지 기간은 `SESSION_TIMEOUT`(기본 7일)이다. 세션 쿠키 이름은 `SESSION`이다.

```bash
# 개발에서 확인하기 (CSRF 쿠키를 먼저 받는다)
curl -c jar -b jar localhost:8080/api/me
curl -c jar -b jar -H "X-XSRF-TOKEN: <jar의 XSRF-TOKEN 값>" -H 'Content-Type: application/json' \
  -X POST localhost:8080/api/auth/signup \
  -d '{"loginId":"BreadFan","email":"fan@example.com","password":"Bread-Lover#2024","nickname":"빵순이","agreedToTerms":true}'
# 서버 로그에 찍힌 verify-email 링크의 token으로 POST /api/auth/verify-email, 이어서 POST /api/auth/login
```

## 데이터 수집기 (`collector`)

대전 5개 구의 빵집을 **카카오 로컬 API**로 모아 DB에 저장하는 별도 프로세스다 (API 서버와 따로 실행한다).
영업시간·메뉴·별점·사진처럼 공식 API가 없는 정보는 **가져오지 않고**, 상세 화면의 **카카오맵 / 네이버 지도 링크**로 원본에서 확인하게 한다.

```bash
# 1) 어떤 작업이 있는지 보기
./gradlew :collector:bootRun
# 2) 저장하지 않고 결과만 미리 보기 (dry-run)
./gradlew :collector:bootRun --args="--collector.job=kakao-bakeries --collector.dry-run=true"
# 3) 실제로 수집해서 저장
./gradlew :collector:bootRun --args="--collector.job=kakao-bakeries"
```

- 필요한 것: `.env`의 `KAKAO_REST_API_KEY` (Kakao Developers에서 **카카오맵 사용 설정**이 켜져 있어야 한다), DB 실행 중 + API 서버로 마이그레이션이 끝난 상태.
- **동작**: 카카오 키워드 검색은 한 번에 최대 45개만 주기 때문에, 대전 전체를 격자 구역으로 나눠 검색하고 **45개가 넘어 잘린 구역은 4등분해서 다시 검색**한다. 결과는 카카오 장소 ID로 중복을 제거한다.
- **저장 규칙**
  - 주소가 대전 5개 구이고, 카테고리가 `제과,베이커리`인 곳만 저장한다.
  - **대형 프랜차이즈는 제외**한다(기본: 파리바게뜨, 뚜레쥬르, 던킨, 크리스피크림). 바꾸려면 `--collector.excluded-names=...`로 목록을 덮어쓴다 (빈 값이면 제외 없음).
  - **카카오 장소 ID 기준으로 추가·갱신**한다. 다시 실행해도 중복으로 쌓이지 않고, 이름·주소·좌표·전화만 갱신한다. 운영에서 정한 값(숨김·폐업 상태, 인기 점수, 영업시간, 인스타 계정)은 **덮어쓰지 않는다**.
- 끝나면 요약을 출력한다: API 호출 수, 검색 결과, 분류별 개수(저장 대상 / 빵집 아님 / 대전 밖 / 제외 이름), 구별 개수, 새로 추가/갱신 개수. `더 쪼갤 수 없는 구역에서 결과가 잘렸어요` 경고가 나오면 그 구역의 일부 빵집이 빠졌을 수 있다는 뜻이다.
- 옵션: `--collector.keywords=빵집,베이커리,제과점`, `--collector.category-keywords=제과,베이커리`, `--collector.area.*`(검색 영역), `--collector.kakao.request-delay-ms`(호출 간격).
- 후기·영업시간·메뉴는 수집하지 않고 원본(카카오맵, 네이버 지도) 링크로 연결한다. 따라서 네이버 검색 API 키는 필요하지 않다 (`NAVER_*`는 네이버 로그인용).
- 앞으로 고려할 작업: 제과점 인허가 데이터로 폐업 여부 교차 확인, 주기적 재수집.

## 테스트

프론트엔드 단위 테스트: `cd frontend && npm test` (vitest)

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
