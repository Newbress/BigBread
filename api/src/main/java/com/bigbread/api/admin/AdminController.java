package com.bigbread.api.admin;

import com.bigbread.api.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 화면 첫 페이지의 요약과 작업 기록. */
@RestController
@Validated
@RequestMapping("/api/admin")
class AdminController {

    private final JdbcClient jdbc;
    private final AdminAudit audit;

    AdminController(JdbcClient jdbc, AdminAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** 처리할 일이 몇 건인지. */
    @GetMapping("/summary")
    Map<String, Long> summary() {
        return jdbc.sql("""
                        SELECT (SELECT count(*) FROM bakery_report WHERE status = 'PENDING') AS pending_bakery_reports,
                               (SELECT count(*) FROM (SELECT 1 FROM content_report WHERE status = 'PENDING'
                                                       GROUP BY target_type, target_id) g) AS pending_content_reports,
                               (SELECT count(*) FROM review WHERE status = 'HIDDEN') AS hidden_reviews,
                               (SELECT count(*) FROM review_photo WHERE status = 'HIDDEN') AS hidden_photos,
                               (SELECT count(*) FROM app_user WHERE suspended_at IS NOT NULL) AS suspended_users
                        """)
                .query((rs, n) -> Map.of(
                        "pendingBakeryReports", rs.getLong("pending_bakery_reports"),
                        "pendingContentReports", rs.getLong("pending_content_reports"),
                        "hiddenReviews", rs.getLong("hidden_reviews"),
                        "hiddenPhotos", rs.getLong("hidden_photos"),
                        "suspendedUsers", rs.getLong("suspended_users")))
                .single();
    }

    @GetMapping("/audit-logs")
    PageResponse<AdminAudit.Entry> auditLogs(@RequestParam(required = false) String targetType,
                                             @RequestParam(required = false) Long targetId,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return audit.list(targetType, targetId, page, size);
    }
}
