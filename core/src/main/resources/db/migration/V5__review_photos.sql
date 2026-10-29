-- 리뷰 사진. 파일 자체는 PhotoStorage(개발: 로컬 폴더, 운영: S3 호환 저장소)에 있고 여기에는 위치만 둔다.
CREATE TABLE review_photo (
    id          BIGSERIAL PRIMARY KEY,
    review_id   BIGINT      NOT NULL REFERENCES review (id) ON DELETE CASCADE,
    -- 저장소 안의 파일 이름 (추측할 수 없는 UUID). 원본 파일명은 저장하지 않는다.
    storage_key VARCHAR(64) NOT NULL UNIQUE,
    width       INTEGER     NOT NULL,
    height      INTEGER     NOT NULL,
    byte_size   INTEGER     NOT NULL,
    seq         SMALLINT    NOT NULL,
    -- HIDDEN: 신고 누적/관리자 처리로 숨긴 사진 (보이지 않지만 증거로 남긴다)
    status      VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (review_id, seq)
);
CREATE INDEX idx_review_photo_review ON review_photo (review_id, seq);
