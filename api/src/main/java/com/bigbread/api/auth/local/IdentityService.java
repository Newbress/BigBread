package com.bigbread.api.auth.local;

import com.bigbread.api.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 본인확인 결과를 1회용 확인표로 바꿔 주고, 확인표를 쓸 때 검사한다.
 * 연계정보(DI)는 서버 비밀값으로 해시한 값만 다루고 저장한다.
 */
@Service
public class IdentityService {

    public enum Purpose { SIGNUP, RESET, FIND_ID }

    /** 확인표 유효 시간. 인증을 마친 뒤 이 안에 가입·재설정을 끝내야 한다. */
    public static final Duration TICKET_TTL = Duration.ofMinutes(10);

    public static final String INVALID_TICKET = "본인 인증이 만료됐거나 유효하지 않아요. 본인 인증을 다시 해 주세요.";

    /** 비밀값이 비어 있는 개발 환경에서만 쓰는 값 (운영은 ProductionChecks 가 비밀값을 요구한다) */
    private static final String DEV_PEPPER = "dev-only-pepper-do-not-use-in-production";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public record Ticket(String ticket, long expiresInSeconds) {
    }

    private final JdbcClient jdbc;
    private final IdentityVerifier verifier;
    private final IdentityProperties properties;
    private final SignupMode mode;

    public IdentityService(JdbcClient jdbc, IdentityVerifier verifier, IdentityProperties properties,
                           @Value("${bigbread.auth.signup-mode:email}") String signupMode) {
        this.jdbc = jdbc;
        this.verifier = verifier;
        this.properties = properties;
        this.mode = SignupMode.parse(signupMode);
    }

    public IdentityVerifier verifier() {
        return verifier;
    }

    /** 본인확인 방식(pass)이 켜져 있고 제공자도 준비됐는가 */
    public boolean enabled() {
        return mode == SignupMode.PASS && verifier.available();
    }

    public void requireEnabled() {
        if (!enabled()) {
            throw ApiException.serviceUnavailable("지금은 본인 인증을 사용할 수 없어요.");
        }
    }

    /** 연계정보를 비밀값으로 해시한다 (HMAC-SHA256, 16진수 64자). */
    public String hash(String di) {
        String pepper = properties.diPepper().isEmpty() ? DEV_PEPPER : properties.diPepper();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(di.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 인증 ID 를 인증 대행사에 확인하고, 통과하면 1회용 확인표를 만든다.
     * 가입용이면 만 나이와 이미 가입한 사람인지도 여기서 미리 알려 준다 (입력을 다 한 뒤에 알게 되지 않도록).
     */
    @Transactional
    public Ticket issue(Purpose purpose, String verificationId) {
        requireEnabled();
        if (verificationId == null || verificationId.isBlank() || verificationId.length() > 200) {
            throw ApiException.badRequest("본인 인증이 완료되지 않았어요. 다시 시도해 주세요.");
        }
        IdentityVerifier.Result result = verifier.verify(verificationId);
        String diHash = hash(result.di());

        if (purpose == Purpose.SIGNUP) {
            if (Period.between(result.birthDate(), LocalDate.now(KST)).getYears() < properties.minAge()) {
                throw ApiException.forbidden("만 " + properties.minAge() + "세 이상만 가입할 수 있어요.");
            }
            if (diTaken(diHash)) {
                throw ApiException.conflict("이미 가입된 본인 인증 정보예요. 아이디 찾기를 이용해 주세요.");
            }
        }

        jdbc.sql("DELETE FROM identity_ticket WHERE created_at < now() - interval '1 day'").update();
        String token = Tokens.newToken();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int inserted = jdbc.sql("""
                        INSERT INTO identity_ticket (token_hash, purpose, di_hash, verification_hash, expires_at)
                        VALUES (:token, :purpose, :di, :verification, :expiresAt)
                        ON CONFLICT (verification_hash) DO NOTHING
                        """)
                .param("token", Tokens.hash(token))
                .param("purpose", purpose.name())
                .param("di", diHash)
                .param("verification", Tokens.hash(verificationId))
                .param("expiresAt", now.plus(TICKET_TTL))
                .update();
        if (inserted == 0) {
            // 이미 한 번 쓴 인증 결과다.
            throw ApiException.badRequest("이미 사용한 본인 인증이에요. 본인 인증을 다시 해 주세요.");
        }
        return new Ticket(token, TICKET_TTL.toSeconds());
    }

    /**
     * 확인표를 한 번 쓴다. 호출한 쪽의 트랜잭션이 실패하면 사용 표시도 되돌려져서, 입력 실수로 인증을 다시 하지 않아도 된다.
     *
     * @return 확인표에 담긴 연계정보 해시. 없거나 만료됐거나 목적이 다르거나 이미 썼으면 빈 값
     */
    @Transactional
    public Optional<String> consume(Purpose purpose, String ticket) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 200) {
            return Optional.empty();
        }
        Optional<String> diHash = jdbc.sql("""
                        SELECT di_hash FROM identity_ticket
                         WHERE token_hash = :hash AND purpose = :purpose AND used_at IS NULL AND expires_at > now()
                         FOR UPDATE
                        """)
                .param("hash", Tokens.hash(ticket)).param("purpose", purpose.name())
                .query(String.class).optional();
        diHash.ifPresent(v -> jdbc.sql("UPDATE identity_ticket SET used_at = now() WHERE token_hash = :hash")
                .param("hash", Tokens.hash(ticket)).update());
        return diHash;
    }

    private boolean diTaken(String diHash) {
        return jdbc.sql("SELECT 1 FROM local_credential WHERE di_hash = :di LIMIT 1")
                .param("di", diHash).query(Integer.class).optional().isPresent();
    }
}
