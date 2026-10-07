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
