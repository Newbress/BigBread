-- 계정 정지: 로그인은 되지만 리뷰·사진·제보·신고 작성이 막힌다
ALTER TABLE app_user
    ADD COLUMN suspended_at     TIMESTAMPTZ,
    ADD COLUMN suspended_reason VARCHAR(200);

-- 운영자 작업 기록. 수정·삭제 기능을 만들지 않는다 (누가 언제 무엇을 했는지 남기는 용도).
CREATE TABLE admin_audit_log (
    id             BIGSERIAL PRIMARY KEY,
    admin_id       BIGINT       REFERENCES app_user (id) ON DELETE SET NULL,
    -- 운영자가 나중에 탈퇴해도 누구였는지 알 수 있게 닉네임을 같이 남긴다
    admin_nickname VARCHAR(50)  NOT NULL,
    action         VARCHAR(40)  NOT NULL,
    target_type    VARCHAR(20)  NOT NULL,
    target_id      BIGINT,
    detail         VARCHAR(1000),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_admin_audit_created ON admin_audit_log (created_at DESC, id DESC);
CREATE INDEX idx_admin_audit_target ON admin_audit_log (target_type, target_id);
