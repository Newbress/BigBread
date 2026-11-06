package com.bigbread.api.admin;

import com.bigbread.api.common.ApiException;
import com.bigbread.api.common.PageResponse;
import com.bigbread.api.report.ContentReportService.TargetType;
import com.bigbread.api.review.BakeryStats;
import com.bigbread.api.review.ReviewService;
import com.bigbread.api.review.photo.PhotoService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰·사진 신고 처리. 신고는 대상별로 묶어서 보여준다.
 * <ul>
 *   <li>DISMISS: 문제없음. 신고만 닫고, 신고 누적으로 자동 숨겨진 것이면 되살린다 (운영자가 직접 숨긴 건 그대로).</li>
 *   <li>HIDE: 숨김 유지/숨기기 (작성자는 수정·삭제할 수 있다).</li>
 *   <li>DELETE: 지우기 (사진 파일 포함).</li>
 *   <li>RESTORE: 이유와 상관없이 숨김을 풀기.</li>
 * </ul>
 */
@Service
public class AdminContentReportService {

    public enum Action { DISMISS, HIDE, DELETE, RESTORE }

    public record Detail(String reason, String detail, String reporterNickname, String status, OffsetDateTime createdAt) {
    }

    public record PhotoView(long id, String url, String thumbUrl, String status) {
    }

    /** 신고 대상 미리보기. 대상이 이미 사라졌으면 exists=false. */
    public record Target(boolean exists, String status, String hiddenReason, Long reviewId, Long bakeryId, String bakeryName,
                         Long authorId, String authorNickname, Integer rating, String content, List<PhotoView> photos) {
    }

    public record Group(String targetType, long targetId, int pendingCount, int totalCount, List<String> reasons,
                        OffsetDateTime firstReportedAt, OffsetDateTime lastReportedAt, Target target, List<Detail> details) {
    }

    private final JdbcClient jdbc;
    private final BakeryStats stats;
    private final ReviewService reviews;
    private final PhotoService photos;
    private final AdminAudit audit;

    public AdminContentReportService(JdbcClient jdbc, BakeryStats stats, ReviewService reviews, PhotoService photos,
                                     AdminAudit audit) {
        this.jdbc = jdbc;
        this.stats = stats;
        this.reviews = reviews;
        this.photos = photos;
        this.audit = audit;
    }

    /** pending=true: 처리 전 신고가 있는 대상(많이 신고된 순), false: 모두 처리된 대상(최근 처리 순). */
    public PageResponse<Group> list(boolean pending, int page, int size) {
        String having = pending ? "count(*) FILTER (WHERE status = 'PENDING') > 0" : "count(*) FILTER (WHERE status = 'PENDING') = 0";
        String order = pending ? "pending DESC, first_at ASC" : "last_handled DESC NULLS LAST";
        long total = jdbc.sql("SELECT count(*) FROM (SELECT 1 FROM content_report GROUP BY target_type, target_id HAVING " + having + ") g")
                .query(Long.class).single();
        record Head(String type, long id, int pending, int total, List<String> reasons, OffsetDateTime first, OffsetDateTime last) {
        }
        List<Head> heads = jdbc.sql("""
                        SELECT target_type, target_id,
                               count(*) FILTER (WHERE status = 'PENDING') AS pending, count(*) AS total,
                               array_agg(DISTINCT reason) AS reasons, min(created_at) AS first_at, max(created_at) AS last_at,
                               max(handled_at) AS last_handled
                          FROM content_report GROUP BY target_type, target_id HAVING """ + " " + having
                        + " ORDER BY " + order + " LIMIT :limit OFFSET :offset")
                .param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> new Head(rs.getString("target_type"), rs.getLong("target_id"), rs.getInt("pending"),
                        rs.getInt("total"), Arrays.asList((String[]) rs.getArray("reasons").getArray()),
                        rs.getObject("first_at", OffsetDateTime.class), rs.getObject("last_at", OffsetDateTime.class)))
                .list();
        List<Group> groups = new ArrayList<>();
        for (Head h : heads) {
            groups.add(new Group(h.type(), h.id(), h.pending(), h.total(), h.reasons(), h.first(), h.last(),
                    target(h.type(), h.id()), details(h.type(), h.id())));
        }
        return PageResponse.of(groups, page, size, total);
    }

    @Transactional
    public void resolve(long adminId, TargetType type, long targetId, Action action) {
        Target target = target(type.name(), targetId);
        if (!target.exists()) {
            closeReports(type, targetId, "ACTIONED", adminId);
            audit.record(adminId, "CONTENT_REPORT_" + action, type.name(), targetId, "대상이 이미 없어 신고만 닫음");
            return;
        }
        String detail;
        switch (action) {
            case DISMISS -> {
                closeReports(type, targetId, "DISMISSED", adminId);
                boolean restored = "HIDDEN".equals(target.status()) && "REPORTS".equals(target.hiddenReason());
                if (restored) {
                    setVisible(type, targetId, target.bakeryId());
                }
                detail = "문제없음" + (restored ? ", 자동 숨김 해제" : "");
            }
            case RESTORE -> {
                closeReports(type, targetId, "DISMISSED", adminId);
                if ("HIDDEN".equals(target.status())) {
                    setVisible(type, targetId, target.bakeryId());
                }
                detail = "숨김 해제";
            }
            case HIDE -> {
                closeReports(type, targetId, "ACTIONED", adminId);
                setHidden(type, targetId, target.bakeryId());
                detail = "운영자 숨김";
            }
            case DELETE -> {
                if (type == TargetType.REVIEW) {
                    reviews.delete(target.authorId(), target.bakeryId());
                } else {
                    photos.delete(target.authorId(), target.bakeryId(), targetId);
                }
                closeReports(type, targetId, "ACTIONED", adminId);
                detail = "삭제 (작성자 " + target.authorNickname() + ")";
            }
            default -> throw new IllegalStateException();
        }
        audit.record(adminId, "CONTENT_REPORT_" + action, type.name(), targetId, detail);
    }

    private void closeReports(TargetType type, long targetId, String status, long adminId) {
        jdbc.sql("""
                        UPDATE content_report SET status = :status, handled_at = now(), handled_by = :admin
                         WHERE target_type = :type AND target_id = :id AND status = 'PENDING'
                        """)
                .param("status", status).param("admin", adminId).param("type", type.name()).param("id", targetId).update();
    }

    private void setVisible(TargetType type, long id, long bakeryId) {
        if (type == TargetType.REVIEW) {
            jdbc.sql("UPDATE review SET status = 'VISIBLE', hidden_reason = NULL WHERE id = :id").param("id", id).update();
            stats.recalculate(bakeryId);
        } else {
            jdbc.sql("UPDATE review_photo SET status = 'VISIBLE', hidden_reason = NULL WHERE id = :id").param("id", id).update();
        }
    }

    private void setHidden(TargetType type, long id, long bakeryId) {
        if (type == TargetType.REVIEW) {
            jdbc.sql("UPDATE review SET status = 'HIDDEN', hidden_reason = 'ADMIN' WHERE id = :id").param("id", id).update();
            stats.recalculate(bakeryId);
        } else {
            jdbc.sql("UPDATE review_photo SET status = 'HIDDEN', hidden_reason = 'ADMIN' WHERE id = :id").param("id", id).update();
        }
    }

    private Target target(String type, long id) {
        Optional<Target> found = "REVIEW".equals(type) ? reviewTarget(id) : photoTarget(id);
        return found.orElse(new Target(false, null, null, null, null, null, null, null, null, null, List.of()));
    }

    private Optional<Target> reviewTarget(long reviewId) {
        return jdbc.sql("""
                        SELECT r.id, r.status, r.hidden_reason, r.bakery_id, b.name AS bakery_name, r.user_id, u.nickname,
                               r.rating, r.content
                          FROM review r JOIN bakery b ON b.id = r.bakery_id JOIN app_user u ON u.id = r.user_id
                         WHERE r.id = :id
                        """)
                .param("id", reviewId)
                .query((rs, n) -> new Target(true, rs.getString("status"), rs.getString("hidden_reason"), rs.getLong("id"),
                        rs.getLong("bakery_id"), rs.getString("bakery_name"), rs.getLong("user_id"), rs.getString("nickname"),
                        rs.getInt("rating"), rs.getString("content"), photosOf(rs.getLong("id"), null)))
                .optional();
    }

    private Optional<Target> photoTarget(long photoId) {
        return jdbc.sql("""
                        SELECT p.id AS photo_id, p.status, p.hidden_reason, r.id AS review_id, r.bakery_id, b.name AS bakery_name,
                               r.user_id, u.nickname, r.rating, r.content
                          FROM review_photo p JOIN review r ON r.id = p.review_id
                          JOIN bakery b ON b.id = r.bakery_id JOIN app_user u ON u.id = r.user_id
                         WHERE p.id = :id
                        """)
                .param("id", photoId)
                .query((rs, n) -> new Target(true, rs.getString("status"), rs.getString("hidden_reason"), rs.getLong("review_id"),
                        rs.getLong("bakery_id"), rs.getString("bakery_name"), rs.getLong("user_id"), rs.getString("nickname"),
                        rs.getInt("rating"), rs.getString("content"), photosOf(rs.getLong("review_id"), rs.getLong("photo_id"))))
                .optional();
    }

    /** 리뷰의 사진(숨김 포함). onlyPhotoId가 있으면 그 한 장만. */
    private List<PhotoView> photosOf(long reviewId, Long onlyPhotoId) {
        return jdbc.sql("SELECT id, storage_key, status FROM review_photo WHERE review_id = :r AND (CAST(:p AS bigint) IS NULL OR id = :p) ORDER BY seq")
                .param("r", reviewId).param("p", onlyPhotoId)
                .query((rs, n) -> new PhotoView(rs.getLong("id"), "/api/admin/photos/" + rs.getString("storage_key"),
                        "/api/admin/photos/" + rs.getString("storage_key") + "/thumb", rs.getString("status")))
                .list();
    }

    private List<Detail> details(String type, long id) {
        return jdbc.sql("""
                        SELECT c.reason, c.detail, u.nickname, c.status, c.created_at
                          FROM content_report c JOIN app_user u ON u.id = c.reporter_id
                         WHERE c.target_type = :type AND c.target_id = :id ORDER BY c.created_at DESC LIMIT 10
                        """)
                .param("type", type).param("id", id)
                .query((rs, n) -> new Detail(rs.getString("reason"), rs.getString("detail"), rs.getString("nickname"),
                        rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    /** 검증용: 처리 요청의 대상이 올바른지 (없는 대상은 404 대신 신고만 닫으므로 호출하지 않는다). */
    static void requireType(TargetType type) {
        if (type == null) {
            throw ApiException.badRequest("대상 종류가 필요해요.");
        }
    }
}
