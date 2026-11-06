# syntax=docker/dockerfile:1
# 대빵 배포 이미지. 저장소 루트를 빌드 컨텍스트로 쓴다:
#   docker build -f deploy/Dockerfile --target api       -t bigbread-api .
#   docker build -f deploy/Dockerfile --target collector -t bigbread-collector .
#   docker build -f deploy/Dockerfile --target web       -t bigbread-web --build-arg KAKAO_JS_KEY=... .

# ---------- 백엔드 빌드 (api, collector 공용) ----------
FROM eclipse-temurin:21-jdk AS jvm-build
WORKDIR /src
# 의존성 받기를 소스보다 먼저 해서, 코드만 바뀌면 이 층은 다시 받지 않는다.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY core/build.gradle.kts core/
COPY api/build.gradle.kts api/
COPY collector/build.gradle.kts collector/
RUN chmod +x gradlew && (./gradlew --no-daemon -q :api:dependencies :collector:dependencies > /dev/null 2>&1 || true)
COPY core core
COPY api api
COPY collector collector
RUN ./gradlew --no-daemon -q :api:bootJar :collector:bootJar -x test \
 && cp "$(ls api/build/libs/*.jar | grep -v plain | head -1)" /api.jar \
 && cp "$(ls collector/build/libs/*.jar | grep -v plain | head -1)" /collector.jar

# ---------- API 서버 ----------
FROM eclipse-temurin:21-jre AS api
# libheif: 아이폰(HEIC) 사진을 JPEG 로 바꾸는 명령(heif-convert). curl: 헬스체크.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libheif-examples libheif-plugin-libde265 curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home app
COPY --from=jvm-build /api.jar /app/app.jar
USER app
WORKDIR /app
# 컨테이너 메모리 한도를 JVM 이 알아서 읽고, 메모리가 모자라면 멈춰서 재시작되게 한다 (반쯤 죽은 채로 남지 않게).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---------- 데이터 수집기 (일회성 실행) ----------
FROM eclipse-temurin:21-jre AS collector
RUN useradd --system --uid 10001 --create-home app
COPY --from=jvm-build /collector.jar /app/collector.jar
USER app
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65"
ENTRYPOINT ["java", "-jar", "/app/collector.jar"]

# ---------- 프론트엔드 빌드 ----------
FROM node:22-alpine AS web-build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json frontend/
RUN cd frontend && npm ci --no-audit --no-fund
COPY frontend frontend
# 카카오 JS 키는 도메인 등록으로 보호되는 공개용 키라 빌드에 넣어도 된다 (REST 키나 비밀 값은 절대 넣지 않는다).
ARG KAKAO_JS_KEY=""
ENV KAKAO_JS_KEY=${KAKAO_JS_KEY}
RUN cd frontend && npm run build

# ---------- 웹 서버 (정적 파일 + API 프록시 + 자동 HTTPS) ----------
FROM caddy:2-alpine AS web
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=web-build /app/frontend/dist /srv
