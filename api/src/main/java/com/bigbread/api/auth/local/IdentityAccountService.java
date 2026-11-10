package com.bigbread.api.auth.local;

import com.bigbread.api.common.ApiException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 본인확인(PASS) 방식의 가입·비밀번호 재설정·아이디 찾기.
 * 확인표를 쓰는 일과 계정을 바꾸는 일을 한 트랜잭션에 묶어서, 중간에 실패하면 확인표도 다시 쓸 수 있게 되돌린다.
 */
@Service
public class IdentityAccountService {

    static final String MISMATCH = "입력한 아이디와 본인 인증 정보가 일치하지 않아요.";

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final IdentityService identity;

    public IdentityAccountService(JdbcClient jdbc, PasswordEncoder encoder, IdentityService identity) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.identity = identity;
    }

    /**
     * 본인확인을 마친 사람이 아이디·비밀번호·닉네임으로 가입한다. 입력은 규칙 검사를 통과한 값이어야 한다.
     *
     * @return 만든 사용자 ID
     * @throws ApiException 400: 확인표가 유효하지 않음 / 409: 이미 쓰는 아이디 또는 이미 가입한 사람
     */
    @Transactional
    public long signup(String ticket, String loginId, String password, String nickname) {
        identity.requireEnabled();
        String diHash = identity.consume(IdentityService.Purpose.SIGNUP, ticket)
                .orElseThrow(() -> ApiException.badRequest(IdentityService.INVALID_TICKET));
        if (exists("SELECT 1 FROM local_credential WHERE login_id = :v", loginId)) {
            throw ApiException.conflict("이미 사용 중인 아이디예요.");
        }
        if (exists("SELECT 1 FROM local_credential WHERE di_hash = :v", diHash)) {
            throw ApiException.conflict("이미 가입된 본인 인증 정보예요. 아이디 찾기를 이용해 주세요.");
        }
        long userId = jdbc.sql("INSERT INTO app_user (nickname) VALUES (:nickname) RETURNING id")
                .param("nickname", nickname).query(Long.class).single();
        int created = jdbc.sql("""
                        INSERT INTO local_credential (user_id, login_id, email, password_hash, terms_agreed_at, di_hash)
                        VALUES (:userId, :loginId, NULL, :hash, now(), :di)
                        ON CONFLICT DO NOTHING
                        """)
                .param("userId", userId).param("loginId", loginId).param("hash", encoder.encode(password)).param("di", diHash)
                .update();
        if (created == 0) {
            throw ApiException.conflict("이미 사용 중인 아이디 또는 이미 가입된 본인 인증 정보예요.");
        }
        return userId;
    }

    /**
     * 본인확인으로 비밀번호를 새로 정한다. 가입 때 확인한 사람과 같을 때만 바뀐다.
     * 아이디가 없는 경우와 사람이 다른 경우를 구분하지 않는다.
     *
     * @return 비밀번호가 바뀐 사용자 ID (호출한 쪽이 모든 기기의 로그인을 끊는다)
     */
    @Transactional
    public long resetPassword(String ticket, String loginId, String newPassword) {
        identity.requireEnabled();
        String error = LocalAuthRules.passwordError(newPassword, loginId);
        if (error != null) {
            throw ApiException.badRequest(error);
        }
        String diHash = identity.consume(IdentityService.Purpose.RESET, ticket)
                .orElseThrow(() -> ApiException.badRequest(IdentityService.INVALID_TICKET));
        Optional<Long> userId = jdbc.sql("SELECT user_id FROM local_credential WHERE login_id = :loginId AND di_hash = :di")
                .param("loginId", loginId).param("di", diHash).query(Long.class).optional();
        if (userId.isEmpty()) {
            throw ApiException.forbidden(MISMATCH);
        }
        jdbc.sql("""
                        UPDATE local_credential
                           SET password_hash = :hash, failed_attempts = 0, locked_until = NULL, updated_at = now()
                         WHERE user_id = :id
                        """)
                .param("hash", encoder.encode(newPassword)).param("id", userId.get()).update();
        return userId.get();
    }

    /** 본인확인을 마친 사람의 아이디를 알려 준다. 가입한 적이 없으면 404. */
    @Transactional
    public String findLoginId(String ticket) {
        identity.requireEnabled();
        String diHash = identity.consume(IdentityService.Purpose.FIND_ID, ticket)
                .orElseThrow(() -> ApiException.badRequest(IdentityService.INVALID_TICKET));
        return jdbc.sql("SELECT login_id FROM local_credential WHERE di_hash = :di")
                .param("di", diHash).query(String.class).optional()
                .orElseThrow(() -> ApiException.notFound("이 본인 인증 정보로 가입된 계정이 없어요."));
    }

    private boolean exists(String sql, String value) {
        return jdbc.sql(sql + " LIMIT 1").param("v", value).query(Integer.class).optional().isPresent();
    }
}
