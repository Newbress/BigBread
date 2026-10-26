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
 * 비밀번호 재설정과 아이디 찾기. 두 기능 모두 "그 계정이 있는지"를 응답으로 알려주지 않는다
 * (없으면 아무 일도 하지 않고 똑같이 끝난다).
 */
@Service
public class AccountRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AccountRecoveryService.class);

    /** 재설정 링크 유효 시간 */
    public static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    public static final String INVALID_RESET_LINK = "유효하지 않거나 만료된 링크예요. 비밀번호 재설정을 다시 요청해 주세요.";

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final AuthMailer mailer;

    public AccountRecoveryService(JdbcClient jdbc, PasswordEncoder encoder, AuthMailer mailer) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.mailer = mailer;
    }

    /**
     * 비밀번호 재설정 링크를 메일로 보낸다. 새로 요청하면 이전 링크는 무효가 된다.
     * 가입된 아이디가 아니면 아무것도 하지 않는다. 메일 발송이 실패해도 호출자에게는 알리지 않는다
     * (실패 여부로 계정이 있는지 드러나지 않게).
     *
     * @throws ApiException 503: 메일을 보낼 수단이 없을 때 (계정 존재 여부와 무관하게 항상 동일)
     */
    @Transactional
    public void requestPasswordReset(String loginId, String linkBase) {
        requireMailer();
        deleteExpiredTokens();

        Optional<Target> target = jdbc.sql("""
                        SELECT c.user_id, c.email, u.nickname
                        FROM local_credential c JOIN app_user u ON u.id = c.user_id
                        WHERE c.login_id = :loginId
                        """)
                .param("loginId", loginId)
                .query((rs, n) -> new Target(rs.getLong("user_id"), rs.getString("email"), rs.getString("nickname")))
                .optional();
        if (target.isEmpty()) {
            return;
        }
        Target t = target.get();

        jdbc.sql("DELETE FROM auth_token WHERE user_id = :userId AND purpose = 'PASSWORD_RESET'")
                .param("userId", t.userId()).update();
        String token = Tokens.newToken();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                        INSERT INTO auth_token (user_id, purpose, token_hash, expires_at)
                        VALUES (:userId, 'PASSWORD_RESET', :hash, :expiresAt)
                        """)
                .param("userId", t.userId())
                .param("hash", Tokens.hash(token))
                .param("expiresAt", now.plus(RESET_TOKEN_TTL))
                .update();
        try {
            mailer.sendPasswordReset(t.email(), t.nickname(), linkBase + "/reset-password?token=" + token, RESET_TOKEN_TTL);
        } catch (RuntimeException e) {
            log.warn("비밀번호 재설정 메일 발송에 실패했습니다: {}", e.getMessage());
        }
    }

    /** 재설정 결과: 비밀번호가 바뀐 사용자와 알림을 보낼 이메일 */
    public record Reset(long userId, String email) {
    }

    /**
     * 메일 링크의 토큰으로 새 비밀번호를 정한다. 성공하면 그 사용자의 다른 재설정 링크는 모두 지우고 잠금도 푼다.
     * 새 비밀번호가 규칙에 맞지 않으면 토큰을 쓰지 않은 채 400으로 거부한다(고쳐서 다시 시도할 수 있게).
     *
     * @return 토큰이 없거나 만료됐으면 빈 값
     */
    @Transactional
    public Optional<Reset> resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank() || token.length() > 200) {
            return Optional.empty();
        }
        deleteExpiredTokens();
        Optional<Pending> pending = jdbc.sql("""
                        SELECT t.user_id, c.login_id, c.email
                        FROM auth_token t JOIN local_credential c ON c.user_id = t.user_id
                        WHERE t.token_hash = :hash AND t.purpose = 'PASSWORD_RESET' AND t.expires_at > now()
                        FOR UPDATE OF t
                        """)
                .param("hash", Tokens.hash(token))
                .query((rs, n) -> new Pending(rs.getLong("user_id"), rs.getString("login_id"), rs.getString("email")))
                .optional();
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        Pending p = pending.get();

        String error = LocalAuthRules.passwordError(newPassword, p.loginId());
        if (error != null) {
            throw ApiException.badRequest(error);
        }

        jdbc.sql("""
                        UPDATE local_credential
                        SET password_hash = :hash, failed_attempts = 0, locked_until = NULL, updated_at = now()
                        WHERE user_id = :userId
                        """)
                .param("hash", encoder.encode(newPassword))
                .param("userId", p.userId())
                .update();
        jdbc.sql("DELETE FROM auth_token WHERE user_id = :userId AND purpose = 'PASSWORD_RESET'")
                .param("userId", p.userId()).update();
        return Optional.of(new Reset(p.userId(), p.email()));
    }

    /** 이 이메일로 가입된 계정이 있으면 아이디를 메일로 알려준다. 없으면 아무것도 하지 않는다. */
    public void sendLoginId(String email) {
        requireMailer();
        Optional<String> loginId = jdbc.sql("SELECT login_id FROM local_credential WHERE email = :email")
                .param("email", email)
                .query(String.class)
                .optional();
        if (loginId.isEmpty()) {
            return;
        }
        try {
            mailer.sendLoginId(email, loginId.get());
        } catch (RuntimeException e) {
            log.warn("아이디 찾기 메일 발송에 실패했습니다: {}", e.getMessage());
        }
    }

    /** 비밀번호가 바뀐 뒤 알림 메일. 실패해도 재설정 자체는 성공으로 둔다. */
    public void notifyPasswordChanged(String email) {
        try {
            mailer.sendPasswordChanged(email);
        } catch (RuntimeException e) {
            log.warn("비밀번호 변경 알림 메일 발송에 실패했습니다: {}", e.getMessage());
        }
    }

    private void requireMailer() {
        if (!mailer.available()) {
            throw ApiException.serviceUnavailable("지금은 이 기능을 사용할 수 없어요.");
        }
    }

    private void deleteExpiredTokens() {
        jdbc.sql("DELETE FROM auth_token WHERE expires_at < now()").update();
    }

    private record Target(long userId, String email, String nickname) {
    }

    private record Pending(long userId, String loginId, String email) {
    }
}
