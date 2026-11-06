package com.bigbread.api.admin;

import com.bigbread.api.common.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 운영자 작업 기록. 기록만 하고 고치거나 지우는 기능은 없다. */
@Component
public class AdminAudit {

    public record Entry(long id, Long adminId, String adminNickname, String action, String targetType, Long targetId,
                        String detail, OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;

    public AdminAudit(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 작업을 한 트랜잭션 안에서 부르면 작업과 기록이 함께 확정되거나 함께 취소된다. */
    public void record(long adminId, String action, String targetType, Long targetId, String detail) {
        String text = detail == null ? null : (detail.length() > 1000 ? detail.substring(0, 997) + "..." : detail);
        jdbc.sql("""
                        INSERT INTO admin_audit_log (admin_id, admin_nickname, action, target_type, target_id, detail)
                        VALUES (:admin, (SELECT nickname FROM app_user WHERE id = :admin), :action, :type, :target, :detail)
                        """)
                .param("admin", adminId).param("action", action).param("type", targetType)
                .param("target", targetId).param("detail", text).update();
    }

    public PageResponse<Entry> list(String targetType, Long targetId, int page, int size) {
        String where = " WHERE (CAST(:type AS varchar) IS NULL OR target_type = :type)"
                + " AND (CAST(:target AS bigint) IS NULL OR target_id = :target)";
        long total = jdbc.sql("SELECT count(*) FROM admin_audit_log" + where)
                .param("type", targetType).param("target", targetId).query(Long.class).single();
        List<Entry> items = jdbc.sql("""
                        SELECT id, admin_id, admin_nickname, action, target_type, target_id, detail, created_at
                          FROM admin_audit_log""" + where + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .param("type", targetType).param("target", targetId)
                .param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> new Entry(rs.getLong("id"), (Long) rs.getObject("admin_id"), rs.getString("admin_nickname"),
                        rs.getString("action"), rs.getString("target_type"), (Long) rs.getObject("target_id"),
                        rs.getString("detail"), rs.getObject("created_at", OffsetDateTime.class)))
                .list();
        return PageResponse.of(items, page, size, total);
    }
}
