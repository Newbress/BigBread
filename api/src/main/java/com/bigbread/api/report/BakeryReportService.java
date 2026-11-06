package com.bigbread.api.report;

import com.bigbread.api.common.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 빵집 제보. 새 빵집(NEW)뿐 아니라 기존 빵집의 폐업(CLOSED)·정보 오류(WRONG_INFO)도 받는다.
 * 제보는 바로 반영되지 않고 운영자가 확인한 뒤 승인한다.
 */
@Service
public class BakeryReportService {

    public static final int MAX_PENDING = 5;
    public static final int MAX_PER_DAY = 10;

    public enum Kind { NEW, CLOSED, WRONG_INFO }

    public record Request(Kind kind, Long bakeryId, String name, String address, String instagram, String note) {
    }

    /** 내가 한 제보. 처리 결과(승인/반려)와 운영자가 남긴 안내가 같이 보인다. */
    public record View(long id, Kind kind, String name, String address, String instagramUsername, String note,
                       String status, String adminNote, Long bakeryId, String bakeryName,
                       OffsetDateTime createdAt, OffsetDateTime reviewedAt) {
    }

    private final JdbcClient jdbc;

    public BakeryReportService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public View create(long userId, Request request) {
        if (request.kind() == null) {
            throw ApiException.badRequest("제보 종류를 골라 주세요.");
        }
        String note = clean(request.note(), 1000, "제보 내용은 1000자까지 쓸 수 있어요.");
        String instagram;
        try {
            instagram = InstagramHandles.normalize(request.instagram());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }

        String name = null;
        String address = null;
        Long bakeryId = null;
        if (request.kind() == Kind.NEW) {
            name = clean(request.name(), 100, "빵집 이름은 100자까지 쓸 수 있어요.");
            address = clean(request.address(), 255, "주소는 255자까지 쓸 수 있어요.");
            if (name == null || address == null) {
                throw ApiException.badRequest("새 빵집은 이름과 주소를 적어 주세요.");
            }
            rejectIfAlreadyRegistered(name, address);
        } else {
            bakeryId = request.bakeryId();
            if (bakeryId == null || !bakeryExists(bakeryId)) {
                throw ApiException.notFound("빵집을 찾을 수 없어요.");
            }
            if (request.kind() == Kind.WRONG_INFO && note == null && instagram == null) {
                throw ApiException.badRequest("어떤 정보가 다른지 적어 주세요.");
            }
            if (alreadyPendingFor(userId, bakeryId, request.kind())) {
                throw ApiException.conflict("이미 같은 제보를 보냈어요. 운영자가 확인 중이에요.");
            }
        }

        Long pending = jdbc.sql("SELECT count(*) FROM bakery_report WHERE user_id = :u AND status = 'PENDING'")
                .param("u", userId).query(Long.class).single();
        if (pending >= MAX_PENDING) {
            throw ApiException.tooManyRequests("확인 중인 제보가 " + MAX_PENDING + "개 있어요. 처리된 뒤에 다시 보내 주세요.");
        }
        Long today = jdbc.sql("SELECT count(*) FROM bakery_report WHERE user_id = :u AND created_at > now() - interval '1 day'")
                .param("u", userId).query(Long.class).single();
        if (today >= MAX_PER_DAY) {
            throw ApiException.tooManyRequests("오늘은 더 제보할 수 없어요. 내일 다시 시도해 주세요.");
        }

        long id = jdbc.sql("""
                        INSERT INTO bakery_report (user_id, kind, name, address, instagram_username, note, bakery_id)
                        VALUES (:u, :kind, :name, :address, :instagram, :note, :bakery) RETURNING id
                        """)
                .param("u", userId).param("kind", request.kind().name()).param("name", name).param("address", address)
                .param("instagram", instagram).param("note", note).param("bakery", bakeryId)
                .query(Long.class).single();
        return mine(userId).stream().filter(v -> v.id() == id).findFirst().orElseThrow();
    }

    /** 최근에 보낸 순서. */
    public List<View> mine(long userId) {
        return jdbc.sql("""
                        SELECT r.id, r.kind, r.name, r.address, r.instagram_username, r.note, r.status, r.admin_note,
                               r.bakery_id, b.name AS bakery_name, r.created_at, r.reviewed_at
                          FROM bakery_report r LEFT JOIN bakery b ON b.id = r.bakery_id
                         WHERE r.user_id = :u ORDER BY r.created_at DESC, r.id DESC LIMIT 50
                        """)
                .param("u", userId)
                .query((rs, n) -> new View(rs.getLong("id"), Kind.valueOf(rs.getString("kind")), rs.getString("name"),
                        rs.getString("address"), rs.getString("instagram_username"), rs.getString("note"),
                        rs.getString("status"), rs.getString("admin_note"), (Long) rs.getObject("bakery_id"),
                        rs.getString("bakery_name"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("reviewed_at", OffsetDateTime.class)))
                .list();
    }

    /** 같은 이름의 빵집이 같은 장소에 이미 있거나 이미 제보됐으면 거절한다 (이름의 공백·대소문자는 무시). */
    private void rejectIfAlreadyRegistered(String name, String address) {
        String normalizedName = squash(name);
        String normalizedAddress = squash(address);
        // 주소가 비어 있는 쪽은 비교하지 않는다 (빈 문자열은 어떤 주소에도 "포함"되어 잘못 일치한다).
        boolean registered = jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM bakery b,
                                 LATERAL (VALUES (regexp_replace(COALESCE(b.road_address, ''), '\\s', '', 'g')),
                                                 (regexp_replace(COALESCE(b.jibun_address, ''), '\\s', '', 'g'))) AS a(addr)
                             WHERE b.status IN ('ACTIVE', 'PENDING')
                               AND lower(regexp_replace(b.name, '\\s', '', 'g')) = :name
                               AND a.addr <> ''
                               AND (position(:address in a.addr) > 0 OR position(a.addr in :address) > 0))
                        """)
                .param("name", normalizedName).param("address", normalizedAddress)
                .query(Boolean.class).single();
        if (registered) {
            throw ApiException.conflict("이미 등록된 빵집이에요. 목록에서 찾아보세요.");
        }
        boolean reported = jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM bakery_report r
                             WHERE r.kind = 'NEW' AND r.status = 'PENDING'
                               AND lower(regexp_replace(r.name, '\\s', '', 'g')) = :name
                               AND (position(:address in regexp_replace(r.address, '\\s', '', 'g')) > 0
                                 OR position(regexp_replace(r.address, '\\s', '', 'g') in :address) > 0))
                        """)
                .param("name", normalizedName).param("address", normalizedAddress)
                .query(Boolean.class).single();
        if (reported) {
            throw ApiException.conflict("이미 같은 제보가 접수됐어요. 운영자가 확인 중이에요.");
        }
    }

    private boolean bakeryExists(long id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM bakery WHERE id = :id AND status IN ('ACTIVE', 'PENDING'))")
                .param("id", id).query(Boolean.class).single();
    }

    private boolean alreadyPendingFor(long userId, long bakeryId, Kind kind) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM bakery_report
                                        WHERE user_id = :u AND bakery_id = :b AND kind = :kind AND status = 'PENDING')
                        """)
                .param("u", userId).param("b", bakeryId).param("kind", kind.name()).query(Boolean.class).single();
    }

    private static String clean(String value, int max, String tooLong) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        if (text.length() > max) {
            throw ApiException.badRequest(tooLong);
        }
        return text;
    }

    private static String squash(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }
}
