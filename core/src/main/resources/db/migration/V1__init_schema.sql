-- 대빵(DaePpang) 초기 스키마
CREATE EXTENSION IF NOT EXISTS postgis;

-- 대전 5개 구
CREATE TABLE district (
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(20) NOT NULL UNIQUE
);

INSERT INTO district (id, code, name) VALUES
    (1, 'DONG',     '동구'),
    (2, 'JUNG',     '중구'),
    (3, 'SEO',      '서구'),
    (4, 'YUSEONG',  '유성구'),
    (5, 'DAEDEOK',  '대덕구');

-- 빵집 (마스터: 카카오 로컬 API + 공공데이터)
CREATE TABLE bakery (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(100)  NOT NULL,
    district_id        SMALLINT      NOT NULL REFERENCES district (id),
    road_address       VARCHAR(255),
    jibun_address      VARCHAR(255),
    location           geometry(Point, 4326) NOT NULL,
    phone              VARCHAR(30),
    kakao_place_id     VARCHAR(30)   UNIQUE,
    public_data_id     VARCHAR(60)   UNIQUE,
    opening_hours      JSONB,
    instagram_username VARCHAR(60),
    popularity_score   DOUBLE PRECISION NOT NULL DEFAULT 0,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'PENDING', 'HIDDEN', 'CLOSED')),
    last_synced_at     TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- 반경 검색: geography 캐스팅 표현식 인덱스 (ST_DWithin(location::geography, ...))
CREATE INDEX idx_bakery_location_geog ON bakery USING GIST ((location::geography));
CREATE INDEX idx_bakery_location      ON bakery USING GIST (location);
CREATE INDEX idx_bakery_district      ON bakery (district_id, popularity_score DESC);
CREATE INDEX idx_bakery_sync          ON bakery (last_synced_at NULLS FIRST);

-- 대표 빵
CREATE TABLE bread (
    id          BIGSERIAL PRIMARY KEY,
    bakery_id   BIGINT       NOT NULL REFERENCES bakery (id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    price       INTEGER,
    signature   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (bakery_id, name)
);

-- 원본 게시물 링크 (네이버 블로그 / 인스타그램)
CREATE TABLE source_link (
    id           BIGSERIAL PRIMARY KEY,
    bakery_id    BIGINT       NOT NULL REFERENCES bakery (id) ON DELETE CASCADE,
    platform     VARCHAR(20)  NOT NULL CHECK (platform IN ('NAVER_BLOG', 'INSTAGRAM')),
    url          VARCHAR(1000) NOT NULL,
    title        VARCHAR(300),
    summary      VARCHAR(1000),
    author_name  VARCHAR(100),
    published_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (bakery_id, url)
);
CREATE INDEX idx_source_link_bakery ON source_link (bakery_id, published_at DESC);

-- 소스별 지표 (인기 점수 계산용, 값이 없으면 0으로 취급)
CREATE TABLE bakery_stat (
    bakery_id    BIGINT       NOT NULL REFERENCES bakery (id) ON DELETE CASCADE,
    source       VARCHAR(20)  NOT NULL CHECK (source IN ('NAVER_BLOG', 'INSTAGRAM', 'INTERNAL')),
    metric       VARCHAR(30)  NOT NULL,
    value        BIGINT       NOT NULL DEFAULT 0,
    collected_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (bakery_id, source, metric)
);

-- 사용자 / 소셜 로그인
CREATE TABLE app_user (
    id                BIGSERIAL PRIMARY KEY,
    email             VARCHAR(255),
    nickname          VARCHAR(50)  NOT NULL,
    profile_image_url VARCHAR(500),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE oauth_account (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider         VARCHAR(20) NOT NULL CHECK (provider IN ('KAKAO', 'NAVER', 'GOOGLE')),
    provider_user_id VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);
CREATE INDEX idx_oauth_account_user ON oauth_account (user_id);

-- 즐겨찾기
CREATE TABLE favorite (
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    bakery_id  BIGINT      NOT NULL REFERENCES bakery (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, bakery_id)
);
CREATE INDEX idx_favorite_bakery ON favorite (bakery_id);

-- 코스(동선) 저장
CREATE TABLE course (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    order_mode  VARCHAR(20)  NOT NULL DEFAULT 'NEAREST' CHECK (order_mode IN ('NEAREST', 'POPULARITY', 'MANUAL')),
    start_point geometry(Point, 4326),
    is_public   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_course_user ON course (user_id, created_at DESC);

CREATE TABLE course_stop (
    course_id BIGINT   NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    seq       SMALLINT NOT NULL,
    bakery_id BIGINT   NOT NULL REFERENCES bakery (id),
    PRIMARY KEY (course_id, seq),
    UNIQUE (course_id, bakery_id)
);

-- 리뷰 / 평점 (사용자당 빵집별 1개)
CREATE TABLE review (
    id         BIGSERIAL PRIMARY KEY,
    bakery_id  BIGINT      NOT NULL REFERENCES bakery (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    rating     SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content    VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (bakery_id, user_id)
);
CREATE INDEX idx_review_bakery ON review (bakery_id, created_at DESC);

-- 빵집 제보 (승인 시 bakery로 반영)
CREATE TABLE bakery_report (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name               VARCHAR(100) NOT NULL,
    address            VARCHAR(255) NOT NULL,
    instagram_username VARCHAR(60),
    note               VARCHAR(1000),
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    bakery_id          BIGINT       REFERENCES bakery (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at        TIMESTAMPTZ
);
CREATE INDEX idx_bakery_report_status ON bakery_report (status, created_at);
