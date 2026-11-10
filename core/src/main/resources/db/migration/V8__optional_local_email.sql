-- 메일 없이 가입하는 모드(AUTH_SIGNUP_MODE=open)에서는 자체 가입 계정에 이메일이 없다.
-- 이메일 유일 인덱스는 NULL 을 여러 개 허용한다.
ALTER TABLE local_credential ALTER COLUMN email DROP NOT NULL;
