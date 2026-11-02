package com.bigbread.api.account;

import com.bigbread.api.auth.local.AccountRecoveryService;
import com.bigbread.api.auth.local.LocalAuthRules;
import com.bigbread.api.common.ApiException;
import com.bigbread.api.report.ContentReportService;
import com.bigbread.api.review.BakeryStats;
import com.bigbread.api.review.photo.PhotoService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 계정: 비밀번호 변경(자체 가입 계정)과 회원 탈퇴.
 * 탈퇴하면 개인 정보와 쓴 글(리뷰·사진 파일·즐겨찾기·코스·제보·신고)이 바로 지워지고 되돌릴 수 없다.
 */
@Service
public class AccountService {

    /** 소셜 로그인 계정이 탈퇴할 때 입력하는 확인 문구. */
    public static final String WITHDRAW_CONFIRM = "탈퇴";

    public record Info(boolean hasPassword, String loginId, List<String> providers) {
    }

    private record Credential(String loginId, String email, String passwordHash) {
    }

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final AccountRecoveryService recovery;
    private final BakeryStats stats;
    private final PhotoService photos;
    private final ContentReportService reports;

    public AccountService(JdbcClient jdbc, PasswordEncoder encoder, AccountRecoveryService recovery, BakeryStats stats,
                          PhotoService photos, ContentReportService reports) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.recovery = recovery;
        this.stats = stats;
        this.photos = photos;
        this.reports = reports;
    }

    public Info info(long userId) {
        Optional<String> loginId = jdbc.sql("SELECT login_id FROM local_credential WHERE user_id = :id")
                .param("id", userId).query(String.class).optional();
        List<String> providers = jdbc.sql("SELECT provider FROM oauth_account WHERE user_id = :id ORDER BY provider")
                .param("id", userId).query(String.class).list();
        return new Info(loginId.isPresent(), loginId.orElse(null), providers);
    }

    /** 현재 비밀번호를 확인한 뒤 새 비밀번호로 바꾼다. 바뀌면 확인 메일을 보내고(실패해도 변경은 유효) 다른 기기의 로그인은 끊는다. */
    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        Credential credential = credential(userId)
                .orElseThrow(() -> ApiException.badRequest("소셜 로그인 계정은 비밀번호가 없어요. 해당 서비스에서 관리해 주세요."));
        requireCurrentPassword(credential, currentPassword);
        if (newPassword != null && newPassword.equals(currentPassword)) {
            throw ApiException.badRequest("현재 비밀번호와 다른 비밀번호를 정해 주세요.");
        }
        String error = LocalAuthRules.passwordError(newPassword, credential.loginId());
        if (error != null) {
            throw ApiException.badRequest(error);
        }
        jdbc.sql("""
                        UPDATE local_credential
                           SET password_hash = :hash, failed_attempts = 0, locked_until = NULL, updated_at = now()
                         WHERE user_id = :id
                        """)
                .param("hash", encoder.encode(newPassword)).param("id", userId).update();
        // 재설정 메일 링크가 남아 있어도 더 못 쓰게 한다.
        jdbc.sql("DELETE FROM auth_token WHERE user_id = :id AND purpose = 'PASSWORD_RESET'").param("id", userId).update();
        recovery.notifyPasswordChanged(credential.email());
    }

    /**
     * 회원 탈퇴. 자체 가입 계정은 비밀번호, 소셜 계정은 확인 문구({@value #WITHDRAW_CONFIRM})로 본인임을 다시 확인한다.
     * 관리자 계정과 정지 중인 계정은 탈퇴할 수 없다 (정지를 피하려는 탈퇴 방지, 마지막 관리자 보호).
     */
    @Transactional
    public void withdraw(long userId, String password, String confirm) {
        String role = jdbc.sql("SELECT role FROM app_user WHERE id = :id").param("id", userId).query(String.class).optional()
                .orElseThrow(() -> ApiException.notFound("계정을 찾을 수 없어요."));
        Optional<Credential> credential = credential(userId);
        if (credential.isPresent()) {
            requireCurrentPassword(credential.get(), password);
        } else if (!WITHDRAW_CONFIRM.equals(confirm == null ? null : confirm.strip())) {
            throw ApiException.badRequest("확인을 위해 '" + WITHDRAW_CONFIRM + "'를 입력해 주세요.");
        }
        if ("ADMIN".equals(role)) {
            throw ApiException.badRequest("관리자 계정은 탈퇴할 수 없어요. 관리자 권한을 먼저 내려 주세요.");
        }
        boolean suspended = jdbc.sql("SELECT suspended_at IS NOT NULL FROM app_user WHERE id = :id").param("id", userId)
                .query(Boolean.class).single();
        if (suspended) {
            throw ApiException.badRequest("정지된 계정은 탈퇴할 수 없어요. 문의해 주세요.");
        }

        // 삭제로 평점·인기 점수가 바뀌는 빵집을 먼저 모은다 (리뷰·즐겨찾기·내가 만든 코스).
        Set<Long> bakeries = new LinkedHashSet<>();
        bakeries.addAll(jdbc.sql("SELECT bakery_id FROM review WHERE user_id = :id").param("id", userId).query(Long.class).list());
        bakeries.addAll(jdbc.sql("SELECT bakery_id FROM favorite WHERE user_id = :id").param("id", userId).query(Long.class).list());
        bakeries.addAll(jdbc.sql("""
                        SELECT s.bakery_id FROM course_stop s JOIN course c ON c.id = s.course_id WHERE c.user_id = :id
                        """).param("id", userId).query(Long.class).list());
        List<Long> reviewIds = jdbc.sql("SELECT id FROM review WHERE user_id = :id").param("id", userId).query(Long.class).list();
        List<Long> photoIds = reviewIds.isEmpty() ? List.of()
                : jdbc.sql("SELECT id FROM review_photo WHERE review_id IN (:ids)").param("ids", reviewIds).query(Long.class).list();

        photos.deleteFilesAfterCommit(reviewIds); // 사진 파일은 삭제가 확정된 뒤에 지운다
        reports.closeForDeleted(ContentReportService.TargetType.PHOTO, photoIds);
        reports.closeForDeleted(ContentReportService.TargetType.REVIEW, reviewIds);
        jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", userId).update(); // 나머지는 FK CASCADE로 함께 지워진다
        stats.recalculate(new ArrayList<>(bakeries));
    }

    private Optional<Credential> credential(long userId) {
        return jdbc.sql("SELECT login_id, email, password_hash FROM local_credential WHERE user_id = :id")
                .param("id", userId)
                .query((rs, n) -> new Credential(rs.getString("login_id"), rs.getString("email"), rs.getString("password_hash")))
                .optional();
    }

    private void requireCurrentPassword(Credential credential, String password) {
        if (password == null || password.isEmpty() || !encoder.matches(password, credential.passwordHash())) {
            throw ApiException.badRequest("현재 비밀번호가 맞지 않아요.");
        }
    }
}
