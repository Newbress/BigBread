-- PASS(휴대폰 본인확인) 가입 모드(AUTH_SIGNUP_MODE=pass).
-- 본인확인 결과의 연계정보(DI)는 서버만 아는 비밀값으로 해시한 값만 저장한다. 이름·생년월일·전화번호는 저장하지 않는다.
-- 한 사람이 자체 가입 계정을 하나만 갖도록 유일하게 둔다 (계정을 지우면 함께 지워진다).
ALTER TABLE local_credential ADD COLUMN di_hash CHAR(64);
CREATE UNIQUE INDEX uq_local_credential_di_hash ON local_credential (di_hash) WHERE di_hash IS NOT NULL;

-- 본인확인을 마친 사람에게 짧게 주는 1회용 확인표. 가입·비밀번호 재설정·아이디 찾기 중 정해진 한 가지에만 쓴다.
CREATE TABLE identity_ticket (
    id                BIGSERIAL PRIMARY KEY,
    -- 확인표 원문은 저장하지 않고 SHA-256 해시만 둔다
    token_hash        CHAR(64)    NOT NULL UNIQUE,
    purpose           VARCHAR(20) NOT NULL CHECK (purpose IN ('SIGNUP', 'RESET', 'FIND_ID')),
    di_hash           CHAR(64)    NOT NULL,
    -- 같은 인증 결과로 확인표를 여러 번 받지 못하게 (인증 대행사의 인증 ID 해시)
    verification_hash CHAR(64)    NOT NULL UNIQUE,
    expires_at        TIMESTAMPTZ NOT NULL,
    used_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_identity_ticket_created ON identity_ticket (created_at);
