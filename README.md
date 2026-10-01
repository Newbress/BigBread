# 대빵 (DaePpang) 🥖

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

## 실행

```bash
cp .env.example .env          # 키 값 채우기 (커밋 금지)
docker compose up -d db       # PostgreSQL + PostGIS
./gradlew :api:bootRun        # http://localhost:8080/api/districts
cd frontend && npm install && npm run dev
```

## 테스트

`api` 통합 테스트는 실제 PostGIS DB가 필요하다. 기본값은 `localhost:5432/daeppang`이며
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 변경할 수 있다.

```bash
./gradlew build
```

## 브랜치 / 커밋 규칙

- 브랜치: `feat/…`, `fix/…`, `chore/…`, `docs/…` (예: `feat/bakery-api`)
- 커밋: `type: 설명` (Conventional Commits)
