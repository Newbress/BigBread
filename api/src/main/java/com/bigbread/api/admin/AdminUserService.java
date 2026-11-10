package com.bigbread.api.admin;

import com.bigbread.api.common.ApiException;
import com.bigbread.api.common.PageResponse;
import com.bigbread.api.auth.local.LocalAuthRules;
import com.bigbread.api.auth.local.SessionInvalidator;
import com.bigbread.api.review.BakeryStats;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 관리(1차): 목록·검색, 상세, 정지/해제, 그 사용자의 글 일괄 숨김.
 * 이메일은 가려서 보여주고, 관리자 권한 변경·강제 탈퇴는 하지 않는다 (2차).
 */
@Service
public class AdminUserService {

    public record Row(long id, String nickname, String loginId, String maskedEmail, List<String> providers, String role,
                      OffsetDateTime createdAt, OffsetDateTime suspendedAt, String suspendedReason, int reviewCount,
                      int photoCount, int reportsReceived) {
    }

    public record ReviewRow(long id, long bakeryId, String bakeryName, int rating, String content, String status,
                            OffsetDateTime createdAt) {
    }

    public record Detail(Row user, List<ReviewRow> recentReviews) {
    }

    private final JdbcClient jdbc;
    private final BakeryStats stats;
    private final AdminAudit audit;
    private final PasswordEncoder encoder;
    private final SessionInvalidator sessions;

    public AdminUserService(JdbcClient jdbc, BakeryStats stats, AdminAudit audit, PasswordEncoder encoder,
                            SessionInvalidator sessions) {
        this.jdbc = jdbc;
        this.stats = stats;
        this.audit = audit;
        this.encoder = encoder;
        this.sessions = sessions;
    }

    /** 임시 비밀번호에 쓰는 글자 (헷갈리는 0/O, 1/l/I 는 뺀다). */
    private static final String TEMP_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String TEMP_DIGITS = "23456789";
    private static final String TEMP_SPECIAL = "!@#$%^*+=?";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record TempPassword(String loginId, String temporaryPassword) {
    }

    /**
     * 비밀번호를 잊은 이용자를 위해 임시 비밀번호로 초기화한다 (메일로 복구할 수 없는 계정용).
     * 새 비밀번호는 이 응답에서 한 번만 보여 주고, 모든 기기의 로그인과 잠금·남은 재설정 링크를 정리한다.
     * 관리자 계정과 소셜 로그인 계정(자체 비밀번호가 없음)은 대상이 아니다.
     */
    @Transactional
    public TempPassword resetPassword(long adminId, long userId) {
        Row user = get(userId).user();
        if (user.loginId() == null) {
            throw ApiException.badRequest("아이디/비밀번호로 가입한 계정만 초기화할 수 있어요.");
        }
        if ("ADMIN".equals(user.role())) {
            throw ApiException.badRequest("관리자 계정의 비밀번호는 초기화할 수 없어요.");
        }
        String password = newTemporaryPassword(user.loginId());
        jdbc.sql("""
                        UPDATE local_credential
                           SET password_hash = :hash, failed_attempts = 0, locked_until = NULL, updated_at = now()
                         WHERE user_id = :id
                        """)
                .param("hash", encoder.encode(password)).param("id", userId).update();
        jdbc.sql("DELETE FROM auth_token WHERE user_id = :id AND purpose = 'PASSWORD_RESET'").param("id", userId).update();
        sessions.invalidateAll(userId);
        audit.record(adminId, "USER_RESET_PASSWORD", "USER", userId, user.nickname() + " (" + user.loginId() + ")");
        return new TempPassword(user.loginId(), password);
    }

    /** 가입 비밀번호 규칙(영문·숫자·특수문자 포함, 아이디 미포함)을 만족하는 12자 무작위 비밀번호. */
    static String newTemporaryPassword(String loginId) {
        String all = TEMP_LETTERS + TEMP_DIGITS + TEMP_SPECIAL;
        while (true) {
            StringBuilder sb = new StringBuilder();
            sb.append(TEMP_LETTERS.charAt(RANDOM.nextInt(TEMP_LETTERS.length())));
            sb.append(TEMP_DIGITS.charAt(RANDOM.nextInt(TEMP_DIGITS.length())));
            sb.append(TEMP_SPECIAL.charAt(RANDOM.nextInt(TEMP_SPECIAL.length())));
            for (int i = 0; i < 9; i++) {
                sb.append(all.charAt(RANDOM.nextInt(all.length())));
            }
            String candidate = shuffle(sb.toString());
            if (LocalAuthRules.passwordError(candidate, loginId) == null) {
                return candidate;
            }
        }
    }

    private static String shuffle(String text) {
        char[] chars = text.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private static final String SELECT = """
            SELECT u.id, u.nickname, lc.login_id, COALESCE(lc.email, u.email) AS email, u.role, u.created_at,
                   u.suspended_at, u.suspended_reason,
                   COALESCE((SELECT array_agg(o.provider ORDER BY o.provider) FROM oauth_account o WHERE o.user_id = u.id), '{}') AS providers,
                   (SELECT count(*) FROM review r WHERE r.user_id = u.id) AS review_count,
                   (SELECT count(*) FROM review_photo p JOIN review r ON r.id = p.review_id WHERE r.user_id = u.id) AS photo_count,
                   (SELECT count(*) FROM content_report c
                     WHERE (c.target_type = 'REVIEW' AND c.target_id IN (SELECT id FROM review WHERE user_id = u.id))
                        OR (c.target_type = 'PHOTO' AND c.target_id IN
                              (SELECT p.id FROM review_photo p JOIN review r ON r.id = p.review_id WHERE r.user_id = u.id))) AS reports_received
              FROM app_user u LEFT JOIN local_credential lc ON lc.user_id = u.id
            """;

    /** q: 닉네임·로그인 아이디 일부, 사용자 번호, 이메일 전체 일치. suspended=true면 정지된 사용자만. */
    public PageResponse<Row> search(String q, boolean suspendedOnly, int page, int size) {
        String text = q == null || q.isBlank() ? null : q.strip();
        String like = text == null ? null : "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Long id = text != null && text.matches("\\d{1,18}") ? Long.parseLong(text) : null;
        String where = """
                 WHERE (CAST(:q AS varchar) IS NULL OR u.nickname ILIKE :like OR lc.login_id ILIKE :like
                        OR u.id = CAST(:id AS bigint) OR lower(COALESCE(lc.email, u.email, '')) = lower(:q))
                   AND (NOT :suspended OR u.suspended_at IS NOT NULL)
                """;
        long total = jdbc.sql("SELECT count(*) FROM app_user u LEFT JOIN local_credential lc ON lc.user_id = u.id" + where)
                .param("q", text).param("like", like).param("id", id).param("suspended", suspendedOnly)
                .query(Long.class).single();
        List<Row> items = jdbc.sql(SELECT + where + " ORDER BY u.id DESC LIMIT :limit OFFSET :offset")
                .param("q", text).param("like", like).param("id", id).param("suspended", suspendedOnly)
                .param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> row(rs)).list();
        return PageResponse.of(items, page, size, total);
    }

    public Detail get(long id) {
        Row row = jdbc.sql(SELECT + " WHERE u.id = :id").param("id", id).query((rs, n) -> row(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없어요."));
        List<ReviewRow> reviews = jdbc.sql("""
                        SELECT r.id, r.bakery_id, b.name, r.rating, r.content, r.status, r.created_at
                          FROM review r JOIN bakery b ON b.id = r.bakery_id
                         WHERE r.user_id = :id ORDER BY r.created_at DESC, r.id DESC LIMIT 20
                        """)
                .param("id", id)
                .query((rs, n) -> new ReviewRow(rs.getLong("id"), rs.getLong("bakery_id"), rs.getString("name"),
                        rs.getInt("rating"), rs.getString("content"), rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
        return new Detail(row, reviews);
    }

    @Transactional
    public Detail suspend(long adminId, long userId, String reason) {
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty()) {
            throw ApiException.badRequest("정지 사유를 적어 주세요.");
        }
        if (text.length() > 200) {
            throw ApiException.badRequest("정지 사유는 200자까지 쓸 수 있어요.");
        }
        Row user = get(userId).user();
        if (userId == adminId) {
            throw ApiException.badRequest("나 자신은 정지할 수 없어요.");
        }
        if ("ADMIN".equals(user.role())) {
            throw ApiException.badRequest("관리자는 정지할 수 없어요. 관리자 권한을 먼저 내려 주세요.");
        }
        if (user.suspendedAt() != null) {
            throw ApiException.conflict("이미 정지된 사용자예요.");
        }
        jdbc.sql("UPDATE app_user SET suspended_at = now(), suspended_reason = :reason, updated_at = now() WHERE id = :id")
                .param("reason", text).param("id", userId).update();
        audit.record(adminId, "USER_SUSPEND", "USER", userId, user.nickname() + ": " + text);
        return get(userId);
    }

    @Transactional
    public Detail unsuspend(long adminId, long userId) {
        Row user = get(userId).user();
        if (user.suspendedAt() == null) {
            throw ApiException.conflict("정지된 사용자가 아니에요.");
        }
        jdbc.sql("UPDATE app_user SET suspended_at = NULL, suspended_reason = NULL, updated_at = now() WHERE id = :id")
                .param("id", userId).update();
        audit.record(adminId, "USER_UNSUSPEND", "USER", userId, user.nickname());
        return get(userId);
    }

    /** 이 사용자의 리뷰와 사진을 모두 숨긴다 (운영자 숨김). 되돌리려면 신고 화면이나 숨김 해제에서 하나씩 푼다. */
    @Transactional
    public Detail hideContent(long adminId, long userId, String reason) {
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty()) {
            throw ApiException.badRequest("숨기는 사유를 적어 주세요.");
        }
        Row user = get(userId).user();
        List<Long> bakeries = jdbc.sql("SELECT DISTINCT bakery_id FROM review WHERE user_id = :id AND status = 'VISIBLE'")
                .param("id", userId).query(Long.class).list();
        int reviews = jdbc.sql("UPDATE review SET status = 'HIDDEN', hidden_reason = 'ADMIN' WHERE user_id = :id AND status = 'VISIBLE'")
                .param("id", userId).update();
        int photos = jdbc.sql("""
                        UPDATE review_photo SET status = 'HIDDEN', hidden_reason = 'ADMIN'
                         WHERE status = 'VISIBLE' AND review_id IN (SELECT id FROM review WHERE user_id = :id)
                        """)
                .param("id", userId).update();
        stats.recalculate(bakeries);
        audit.record(adminId, "USER_HIDE_CONTENT", "USER", userId,
                user.nickname() + ": 리뷰 " + reviews + "개, 사진 " + photos + "장 숨김 (" + text + ")");
        return get(userId);
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getLong("id"), rs.getString("nickname"), rs.getString("login_id"), mask(rs.getString("email")),
                Arrays.asList((String[]) rs.getArray("providers").getArray()), rs.getString("role"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("suspended_at", OffsetDateTime.class),
                rs.getString("suspended_reason"), rs.getInt("review_count"), rs.getInt("photo_count"),
                rs.getInt("reports_received"));
    }

    /** abc@example.com → a**@example.com */
    static String mask(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, 1).toLowerCase(Locale.ROOT) + "**" + email.substring(at);
    }
}
