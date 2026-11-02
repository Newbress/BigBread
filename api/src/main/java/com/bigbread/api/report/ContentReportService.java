package com.bigbread.api.report;

import com.bigbread.api.auth.AccountStatus;
import com.bigbread.api.common.ApiException;
import com.bigbread.api.review.BakeryStats;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰·사진 신고. 서로 다른 사용자 {@value #HIDE_THRESHOLD}명이 신고하면(처리 전 신고 기준) 운영자가 확인할 때까지 자동으로 숨긴다.
 * 신고한 사람은 작성자에게 알려지지 않고, 한 사람이 같은 대상을 한 번만 신고할 수 있다.
 */
@Service
public class ContentReportService {

    /** 이 수만큼 신고가 쌓이면 자동으로 숨긴다. */
    public static final int HIDE_THRESHOLD = 3;
    /** 한 사람이 하루에 할 수 있는 신고 수 (무더기 신고로 남의 글을 숨기는 것을 막는다). */
    public static final int MAX_PER_DAY = 20;

    public enum TargetType { REVIEW, PHOTO }

    public enum Reason { SPAM, ABUSE, PRIVACY, INAPPROPRIATE, OTHER }

    private final JdbcClient jdbc;
    private final BakeryStats stats;
    private final AccountStatus accountStatus;

    public ContentReportService(JdbcClient jdbc, BakeryStats stats, AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
        this.jdbc = jdbc;
        this.stats = stats;
    }

    @Transactional
    public void report(long reporterId, TargetType type, long targetId, Reason reason, String detail) {
        accountStatus.requireActive(reporterId);
        Target target = findVisibleTarget(type, targetId)
                .orElseThrow(() -> ApiException.notFound("이미 삭제됐거나 볼 수 없는 " + label(type) + "이에요."));
        if (target.authorId() == reporterId) {
            throw ApiException.badRequest("내가 쓴 " + label(type) + "은 신고할 수 없어요.");
        }
        String text = detail == null || detail.isBlank() ? null : detail.strip();
        if (text != null && text.length() > 500) {
            throw ApiException.badRequest("신고 내용은 500자까지 쓸 수 있어요.");
        }
        Long today = jdbc.sql("SELECT count(*) FROM content_report WHERE reporter_id = :u AND created_at > now() - interval '1 day'")
                .param("u", reporterId).query(Long.class).single();
        if (today >= MAX_PER_DAY) {
            throw ApiException.tooManyRequests("오늘은 더 신고할 수 없어요. 내일 다시 시도해 주세요.");
        }
        int inserted = jdbc.sql("""
                        INSERT INTO content_report (reporter_id, target_type, target_id, reason, detail)
                        VALUES (:u, :type, :id, :reason, :detail)
                        ON CONFLICT (reporter_id, target_type, target_id) DO NOTHING
                        """)
                .param("u", reporterId).param("type", type.name()).param("id", targetId)
                .param("reason", reason.name()).param("detail", text).update();
        if (inserted == 0) {
            throw ApiException.conflict("이미 신고했어요. 운영자가 확인할게요.");
        }
        Long pending = jdbc.sql("""
                        SELECT count(*) FROM content_report
                         WHERE target_type = :type AND target_id = :id AND status = 'PENDING'
                        """)
                .param("type", type.name()).param("id", targetId).query(Long.class).single();
        if (pending >= HIDE_THRESHOLD) {
            hide(type, targetId, target.bakeryId());
        }
    }

    /** 리뷰·사진이 (작성자 삭제 등으로) 사라졌을 때, 처리 전 신고를 처리됨으로 닫는다. */
    @Transactional
    public void closeForDeleted(TargetType type, Collection<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return;
        }
        jdbc.sql("""
                        UPDATE content_report SET status = 'ACTIONED', handled_at = now()
                         WHERE target_type = :type AND target_id IN (:ids) AND status = 'PENDING'
                        """)
                .param("type", type.name()).param("ids", targetIds).update();
    }

    private void hide(TargetType type, long targetId, long bakeryId) {
        if (type == TargetType.REVIEW) {
            int hidden = jdbc.sql("UPDATE review SET status = 'HIDDEN', hidden_reason = 'REPORTS' WHERE id = :id AND status = 'VISIBLE'")
                    .param("id", targetId).update();
            if (hidden > 0) {
                stats.recalculate(bakeryId);
            }
        } else {
            jdbc.sql("UPDATE review_photo SET status = 'HIDDEN', hidden_reason = 'REPORTS' WHERE id = :id AND status = 'VISIBLE'")
                    .param("id", targetId).update();
        }
    }

    private record Target(long authorId, long bakeryId) {
    }

    /** 신고할 수 있는 (보이는) 대상과 작성자·빵집. */
    private Optional<Target> findVisibleTarget(TargetType type, long id) {
        String sql = type == TargetType.REVIEW
                ? "SELECT r.user_id, r.bakery_id FROM review r WHERE r.id = :id AND r.status = 'VISIBLE'"
                : """
                  SELECT r.user_id, r.bakery_id FROM review_photo p JOIN review r ON r.id = p.review_id
                   WHERE p.id = :id AND p.status = 'VISIBLE' AND r.status = 'VISIBLE'
                  """;
        List<Target> found = jdbc.sql(sql).param("id", id)
                .query((rs, n) -> new Target(rs.getLong("user_id"), rs.getLong("bakery_id"))).list();
        return found.stream().findFirst();
    }

    private static String label(TargetType type) {
        return type == TargetType.REVIEW ? "리뷰" : "사진";
    }
}
