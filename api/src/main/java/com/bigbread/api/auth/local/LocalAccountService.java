package com.bigbread.api.auth.local;

import com.bigbread.api.common.ApiException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자체 가입(아이디/비밀번호)과 로그인 확인.
 *
 * <p>가입 요청은 인증 대기(pending_signup)에만 저장하고, 메일 링크로 인증이 끝나야 계정(app_user + local_credential)을 만든다.
 * 그래서 남의 이메일·아이디를 미리 점유하는 계정이 생기지 않는다.
 */
@Service
public class LocalAccountService {

    private static final Logger log = LoggerFactory.getLogger(LocalAccountService.class);

    /** 가입 인증 링크 유효 시간 */
    public static final Duration SIGNUP_TOKEN_TTL = Duration.ofHours(24);

    public static final String INVALID_LINK = "유효하지 않거나 만료된 링크예요. 가입을 다시 진행해 주세요.";

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final AuthMailer mailer;
    /** 없는 아이디로 로그인을 시도해도 비밀번호 검증과 비슷한 시간이 걸리도록 미리 만들어 둔 해시 */
    private final String dummyHash;

    public LocalAccountService(JdbcClient jdbc, PasswordEncoder encoder, AuthMailer mailer) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.mailer = mailer;
        this.dummyHash = encoder.encode("dummy-password-for-timing-equalization");
    }

    public boolean signupAvailable() {
        return mailer.available();
    }

    /**
     * 가입 요청. 입력은 이미 규칙 검사를 통과한 값이어야 한다.
     * 이미 가입된 이메일이어도 호출자에게는 정상과 똑같이 끝난다 (이메일 주인에게만 메일로 알린다).
     *
     * @throws ApiException 409: 이미 사용 중인 아이디
     */
    @Transactional
    public void requestSignup(String loginId, String email, String password, String nickname, String linkBase) {
        if (!mailer.available()) {
            throw ApiException.serviceUnavailable("지금은 회원가입을 사용할 수 없어요.");
        }
        // 이메일 존재 여부와 상관없이 같은 일을 하도록 해시는 항상 먼저 계산한다 (응답 시간 차이를 줄인다).
        String passwordHash = encoder.encode(password);

        jdbc.sql("DELETE FROM pending_signup WHERE expires_at < now()").update();

        boolean takenByOtherPending = jdbc.sql("SELECT 1 FROM pending_signup WHERE login_id = :loginId AND email <> :email LIMIT 1")
                .param("loginId", loginId).param("email", email).query(Integer.class).optional().isPresent();
        if (exists("SELECT 1 FROM local_credential WHERE login_id = :v", loginId) || takenByOtherPending) {
            throw ApiException.conflict("이미 사용 중인 아이디예요.");
        }
        if (exists("SELECT 1 FROM local_credential WHERE email = :v", email)) {
            mailer.sendAlreadyRegistered(email);
            return;
        }

        // 같은 이메일로 다시 요청하면 이전 링크는 무효가 된다.
        jdbc.sql("DELETE FROM pending_signup WHERE email = :email OR login_id = :loginId")
                .param("email", email).param("loginId", loginId).update();

        String token = Tokens.newToken();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                        INSERT INTO pending_signup (login_id, email, password_hash, nickname, terms_agreed_at, token_hash, expires_at)
                        VALUES (:loginId, :email, :hash, :nickname, :agreedAt, :tokenHash, :expiresAt)
                        """)
                .param("loginId", loginId)
                .param("email", email)
                .param("hash", passwordHash)
                .param("nickname", nickname)
                .param("agreedAt", now)
                .param("tokenHash", Tokens.hash(token))
                .param("expiresAt", now.plus(SIGNUP_TOKEN_TTL))
                .update();

        mailer.sendSignupVerification(email, nickname, linkBase + "/verify-email?token=" + token, SIGNUP_TOKEN_TTL);
    }

    /**
     * 메일 링크의 토큰으로 가입을 완료한다. 토큰은 한 번만 쓸 수 있다.
     *
     * @return 만든 사용자 ID, 토큰이 없거나 만료됐거나 그 사이 아이디·이메일이 사용 중이 되면 빈 값
     */
    @Transactional
    public Optional<Long> verifySignup(String token) {
        if (token == null || token.isBlank() || token.length() > 200) {
            return Optional.empty();
        }
        Optional<Pending> pending = jdbc.sql("""
                        SELECT id, login_id, email, password_hash, nickname, terms_agreed_at
                        FROM pending_signup WHERE token_hash = :hash AND expires_at > now() FOR UPDATE
                        """)
                .param("hash", Tokens.hash(token))
                .query((rs, n) -> new Pending(rs.getLong("id"), rs.getString("login_id"), rs.getString("email"),
                        rs.getString("password_hash"), rs.getString("nickname"),
                        rs.getObject("terms_agreed_at", OffsetDateTime.class)))
                .optional();
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        Pending p = pending.get();
        // 한 번 쓴 토큰은 결과와 상관없이 다시 쓸 수 없게 지운다.
        jdbc.sql("DELETE FROM pending_signup WHERE id = :id").param("id", p.id()).update();

        long userId = jdbc.sql("INSERT INTO app_user (email, nickname) VALUES (:email, :nickname) RETURNING id")
                .param("email", p.email())
                .param("nickname", p.nickname())
                .query(Long.class)
                .single();
        int created = jdbc.sql("""
                        INSERT INTO local_credential (user_id, login_id, email, password_hash, terms_agreed_at)
                        VALUES (:userId, :loginId, :email, :hash, :agreedAt)
                        ON CONFLICT DO NOTHING
                        """)
                .param("userId", userId)
                .param("loginId", p.loginId())
                .param("email", p.email())
                .param("hash", p.passwordHash())
                .param("agreedAt", p.termsAgreedAt())
                .update();
        if (created == 0) {
            // 인증을 기다리는 사이 같은 아이디나 이메일이 먼저 가입됐다.
            jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", userId).update();
            log.info("가입 인증 중 아이디 또는 이메일이 이미 사용 중이라 가입을 취소했습니다.");
            return Optional.empty();
        }
        return Optional.of(userId);
    }

    /** 연속으로 틀릴 수 있는 횟수. 넘으면 {@link #LOCK_DURATION} 동안 로그인할 수 없다. */
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    public enum LoginStatus { SUCCESS, FAILED, LOCKED }

    public record LoginResult(LoginStatus status, long userId) {
        static LoginResult failed() {
            return new LoginResult(LoginStatus.FAILED, 0);
        }
    }

    /**
     * 아이디와 비밀번호를 확인한다. 아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다.
     *
     * <p>연속 {@value #MAX_FAILED_ATTEMPTS}번 틀리면 계정을 15분 동안 잠근다. 잠긴 동안에는 비밀번호가 맞아도
     * 거부한다 (공격자가 계속 시도하다 맞히는 것을 막는다). 성공하면 실패 횟수를 지운다.
     */
    @Transactional
    public LoginResult authenticate(String loginIdInput, String password) {
        String loginId = LocalAuthRules.normalizeLoginId(loginIdInput);
        if (loginId == null || password == null || password.length() > LocalAuthRules.PASSWORD_MAX) {
            encoder.matches("x", dummyHash);
            return LoginResult.failed();
        }
        Optional<Credential> credential = jdbc.sql("""
                        SELECT user_id, password_hash, (locked_until IS NOT NULL AND locked_until > now()) AS locked
                        FROM local_credential WHERE login_id = :id
                        """)
                .param("id", loginId)
                .query((rs, n) -> new Credential(rs.getLong("user_id"), rs.getString("password_hash"), rs.getBoolean("locked")))
                .optional();
        if (credential.isEmpty()) {
            encoder.matches(password, dummyHash);
            return LoginResult.failed();
        }
        Credential c = credential.get();
        if (c.locked()) {
            return new LoginResult(LoginStatus.LOCKED, c.userId());
        }
        if (!encoder.matches(password, c.passwordHash())) {
            recordFailure(c.userId());
            return LoginResult.failed();
        }
        if (encoder.upgradeEncoding(c.passwordHash())) {
            jdbc.sql("UPDATE local_credential SET password_hash = :hash, updated_at = now() WHERE user_id = :id")
                    .param("hash", encoder.encode(password))
                    .param("id", c.userId())
                    .update();
        }
        jdbc.sql("""
                        UPDATE local_credential SET failed_attempts = 0, locked_until = NULL
                        WHERE user_id = :id AND (failed_attempts <> 0 OR locked_until IS NOT NULL)
                        """)
                .param("id", c.userId())
                .update();
        return new LoginResult(LoginStatus.SUCCESS, c.userId());
    }

    /** 실패 횟수를 1 올리고, 한도에 닿으면 잠근다. 한 문장으로 처리해 동시에 여러 번 시도해도 정확히 센다. */
    private void recordFailure(long userId) {
        jdbc.sql("""
                        UPDATE local_credential SET
                            locked_until = CASE WHEN failed_attempts + 1 >= :max
                                                THEN now() + make_interval(secs => :lockSeconds) ELSE locked_until END,
                            failed_attempts = CASE WHEN failed_attempts + 1 >= :max THEN 0 ELSE failed_attempts + 1 END,
                            updated_at = now()
                        WHERE user_id = :id
                        """)
                .param("max", MAX_FAILED_ATTEMPTS)
                .param("lockSeconds", LOCK_DURATION.toSeconds())
                .param("id", userId)
                .update();
    }

    public boolean loginIdTaken(String loginId) {
        jdbc.sql("DELETE FROM pending_signup WHERE expires_at < now()").update();
        return exists("SELECT 1 FROM local_credential WHERE login_id = :v", loginId)
                || exists("SELECT 1 FROM pending_signup WHERE login_id = :v", loginId);
    }

    private boolean exists(String sql, String value) {
        return jdbc.sql(sql + " LIMIT 1").param("v", value).query(Integer.class).optional().isPresent();
    }

    private record Pending(long id, String loginId, String email, String passwordHash, String nickname,
                           OffsetDateTime termsAgreedAt) {
    }

    private record Credential(long userId, String passwordHash, boolean locked) {
    }
}
