-- 자체 로그인(아이디/비밀번호)
-- 인증 완료 후에만 계정을 만든다: 가입 요청은 pending_signup에 두고, 메일 링크로 인증하면 app_user + local_credential을 만든다.

CREATE TABLE local_credential (
    user_id         BIGINT PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    -- 소문자로 정규화해 저장한다 (대소문자 구분 없는 아이디)
    login_id        VARCHAR(20)  NOT NULL CHECK (login_id ~ '^[a-z][a-z0-9]{4,19}$'),
    -- 인증이 끝난 이메일 (소문자)
    email           VARCHAR(255) NOT NULL CHECK (email = lower(email)),
    password_hash   VARCHAR(255) NOT NULL,
    terms_agreed_at TIMESTAMPTZ  NOT NULL,
    -- 무차별 대입 방어용 (2단계에서 사용)
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_local_credential_login_id ON local_credential (login_id);
CREATE UNIQUE INDEX uq_local_credential_email ON local_credential (email);

CREATE TABLE pending_signup (
    id              BIGSERIAL PRIMARY KEY,
    login_id        VARCHAR(20)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    nickname        VARCHAR(50)  NOT NULL,
    terms_agreed_at TIMESTAMPTZ  NOT NULL,
    -- 메일 링크의 토큰은 원문이 아니라 SHA-256 해시만 저장한다
    token_hash      CHAR(64)     NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_pending_signup_login_id ON pending_signup (login_id);
CREATE INDEX idx_pending_signup_email ON pending_signup (email);
CREATE INDEX idx_pending_signup_expires ON pending_signup (expires_at);
