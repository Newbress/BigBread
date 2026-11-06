-- 숨김 사유: REPORTS = 신고가 쌓여 자동 숨김(운영자가 기각하면 되살린다), ADMIN = 운영자가 직접 숨김
ALTER TABLE review
    ADD COLUMN hidden_reason VARCHAR(10) CHECK (hidden_reason IN ('REPORTS', 'ADMIN'));
ALTER TABLE review_photo
    ADD COLUMN hidden_reason VARCHAR(10) CHECK (hidden_reason IN ('REPORTS', 'ADMIN'));

-- 빵집 제보: 새 빵집(NEW) 말고도 기존 빵집의 폐업(CLOSED)·정보 오류(WRONG_INFO) 제보를 받는다.
-- bakery_id: NEW는 승인 후 만들어진 빵집, CLOSED/WRONG_INFO는 대상 빵집
ALTER TABLE bakery_report
    ADD COLUMN kind       VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (kind IN ('NEW', 'CLOSED', 'WRONG_INFO')),
    ADD COLUMN admin_note VARCHAR(500);
ALTER TABLE bakery_report
    ALTER COLUMN name DROP NOT NULL,
    ALTER COLUMN address DROP NOT NULL;
ALTER TABLE bakery_report
    ADD CONSTRAINT bakery_report_new_fields CHECK (kind <> 'NEW' OR (name IS NOT NULL AND address IS NOT NULL));
CREATE INDEX idx_bakery_report_user ON bakery_report (user_id, created_at DESC);

-- 리뷰·사진 신고. 한 사람이 같은 대상을 한 번만 신고할 수 있다.
CREATE TABLE content_report (
    id          BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    target_type VARCHAR(10)  NOT NULL CHECK (target_type IN ('REVIEW', 'PHOTO')),
    target_id   BIGINT       NOT NULL,
    reason      VARCHAR(20)  NOT NULL CHECK (reason IN ('SPAM', 'ABUSE', 'PRIVACY', 'INAPPROPRIATE', 'OTHER')),
    detail      VARCHAR(500),
    -- DISMISSED: 문제 없음, ACTIONED: 숨김/삭제로 처리됨 (대상이 지워진 경우 포함)
    status      VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DISMISSED', 'ACTIONED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    handled_at  TIMESTAMPTZ,
    handled_by  BIGINT       REFERENCES app_user (id) ON DELETE SET NULL,
    UNIQUE (reporter_id, target_type, target_id)
);
CREATE INDEX idx_content_report_target ON content_report (target_type, target_id, status);
CREATE INDEX idx_content_report_queue ON content_report (status, created_at);
