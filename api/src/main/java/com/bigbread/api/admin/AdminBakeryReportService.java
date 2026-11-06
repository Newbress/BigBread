package com.bigbread.api.admin;

import com.bigbread.api.common.ApiException;
import com.bigbread.api.common.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 빵집 제보 처리: 새 빵집은 빵집으로 등록, 폐업은 닫기, 정보 오류는 확인 표시, 반려는 이유를 남긴다. */
@Service
public class AdminBakeryReportService {

    public record Row(long id, String kind, String status, String name, String address, String instagramUsername,
                      String note, String adminNote, Long bakeryId, String bakeryName, String bakeryStatus,
                      long reporterId, String reporterNickname, OffsetDateTime createdAt, OffsetDateTime reviewedAt) {
    }

    /** 운영자가 후보 목록에서 고른 위치 (카카오 장소 검색 결과). 주소로만 찾은 위치는 kakaoPlaceId가 비어 있다. */
    public record PlaceChoice(String kakaoPlaceId, String roadAddress, String jibunAddress, Double lat, Double lng, String phone) {
    }

    /** 승인할 때 운영자가 덧붙이는 값. 위치는 후보에서 고르거나(place), 그것도 안 되면 lat/lng/districtCode를 직접 넣는다. */
    public record Approval(Double lat, Double lng, String districtCode, Boolean applyInstagram, String adminNote,
                           PlaceChoice place) {
    }

    private final JdbcClient jdbc;
    private final PlaceLookup lookup;
    private final AdminAudit audit;

    public AdminBakeryReportService(JdbcClient jdbc, PlaceLookup lookup, AdminAudit audit) {
        this.jdbc = jdbc;
        this.lookup = lookup;
        this.audit = audit;
    }

    /** status: PENDING | APPROVED | REJECTED | null(전체). 확인할 것(PENDING)은 오래된 순, 나머지는 최근 순. */
    public PageResponse<Row> list(String status, int page, int size) {
        String where = " WHERE (CAST(:status AS varchar) IS NULL OR r.status = :status)";
        long total = jdbc.sql("SELECT count(*) FROM bakery_report r" + where).param("status", status).query(Long.class).single();
        String order = "PENDING".equals(status) ? "r.created_at ASC, r.id ASC" : "r.created_at DESC, r.id DESC";
        List<Row> items = jdbc.sql("""
                        SELECT r.id, r.kind, r.status, r.name, r.address, r.instagram_username, r.note, r.admin_note,
                               r.bakery_id, b.name AS bakery_name, b.status AS bakery_status,
                               u.id AS reporter_id, u.nickname, r.created_at, r.reviewed_at
                          FROM bakery_report r
                          JOIN app_user u ON u.id = r.user_id
                          LEFT JOIN bakery b ON b.id = r.bakery_id""" + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset")
                .param("status", status).param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("kind"), rs.getString("status"),
                        rs.getString("name"), rs.getString("address"), rs.getString("instagram_username"),
                        rs.getString("note"), rs.getString("admin_note"), (Long) rs.getObject("bakery_id"),
                        rs.getString("bakery_name"), rs.getString("bakery_status"), rs.getLong("reporter_id"),
                        rs.getString("nickname"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("reviewed_at", OffsetDateTime.class)))
                .list();
        return PageResponse.of(items, page, size, total);
    }

    @Transactional
    public Row approve(long adminId, long reportId, Approval approval) {
        Pending report = lockPending(reportId);
        String note = trim(approval.adminNote(), 500);
        Long bakeryId = report.bakeryId();
        String detail;
        switch (report.kind()) {
            case "NEW" -> {
                bakeryId = createBakery(report, approval);
                detail = "새 빵집 등록: " + report.name() + " (빵집 " + bakeryId + ")";
            }
            case "CLOSED" -> {
                requireBakery(bakeryId);
                jdbc.sql("UPDATE bakery SET status = 'CLOSED', updated_at = now() WHERE id = :id").param("id", bakeryId).update();
                detail = "폐업 처리: 빵집 " + bakeryId;
            }
            default -> {
                requireBakery(bakeryId);
                detail = "정보 오류 확인: 빵집 " + bakeryId;
                if (Boolean.TRUE.equals(approval.applyInstagram()) && report.instagram() != null) {
                    jdbc.sql("UPDATE bakery SET instagram_username = :ig, updated_at = now() WHERE id = :id")
                            .param("ig", report.instagram()).param("id", bakeryId).update();
                    detail += ", 인스타 " + report.instagram() + " 반영";
                }
            }
        }
        jdbc.sql("""
                        UPDATE bakery_report SET status = 'APPROVED', bakery_id = :bakery, admin_note = :note, reviewed_at = now()
                         WHERE id = :id
                        """)
                .param("bakery", bakeryId).param("note", note).param("id", reportId).update();
        audit.record(adminId, "BAKERY_REPORT_APPROVE", "BAKERY_REPORT", reportId, detail);
        return one(reportId);
    }

    @Transactional
    public Row reject(long adminId, long reportId, String adminNote) {
        lockPending(reportId);
        String note = trim(adminNote, 500);
        if (note == null) {
            throw ApiException.badRequest("반려 사유를 적어 주세요. 제보한 사람에게 보여요.");
        }
        jdbc.sql("UPDATE bakery_report SET status = 'REJECTED', admin_note = :note, reviewed_at = now() WHERE id = :id")
                .param("note", note).param("id", reportId).update();
        audit.record(adminId, "BAKERY_REPORT_REJECT", "BAKERY_REPORT", reportId, note);
        return one(reportId);
    }

    private long createBakery(Pending report, Approval approval) {
        PlaceLookup.Place place = null;
        double lat;
        double lng;
        Short districtId;
        String roadAddress = null;
        String jibunAddress = report.address();
        String phone = null;
        String kakaoPlaceId = null;

        if (approval.place() != null) {
            PlaceChoice choice = approval.place();
            if (choice.lat() == null || choice.lng() == null || choice.lat() < 33 || choice.lat() > 39
                    || choice.lng() < 124 || choice.lng() > 132) {
                throw ApiException.badRequest("고른 위치의 좌표가 올바르지 않아요.");
            }
            lat = choice.lat();
            lng = choice.lng();
            requireMax(choice.kakaoPlaceId(), 30, "카카오 장소 ID");
            requireMax(choice.phone(), 30, "전화번호");
            requireMax(choice.roadAddress(), 255, "도로명 주소");
            requireMax(choice.jibunAddress(), 255, "지번 주소");
            roadAddress = blankToNull(choice.roadAddress());
            jibunAddress = blankToNull(choice.jibunAddress()) != null ? choice.jibunAddress() : report.address();
            phone = blankToNull(choice.phone());
            kakaoPlaceId = blankToNull(choice.kakaoPlaceId());
            districtId = districtOf(roadAddress != null ? roadAddress : jibunAddress);
            if (districtId == null) {
                throw ApiException.badRequest("대전 동구·중구·서구·유성구·대덕구가 아니에요. (" + jibunAddress + ")");
            }
            if (kakaoPlaceId != null && jdbc.sql("SELECT EXISTS (SELECT 1 FROM bakery WHERE kakao_place_id = :id)")
                    .param("id", kakaoPlaceId).query(Boolean.class).single()) {
                throw ApiException.conflict("이미 등록된 빵집이에요. (카카오 장소 " + kakaoPlaceId + ")");
            }
        } else if (approval.lat() != null && approval.lng() != null) {
            lat = approval.lat();
            lng = approval.lng();
            if (lat < 33 || lat > 39 || lng < 124 || lng > 132) {
                throw ApiException.badRequest("좌표가 올바르지 않아요.");
            }
            if (approval.districtCode() == null || approval.districtCode().isBlank()) {
                throw ApiException.badRequest("좌표를 직접 넣을 때는 구도 골라 주세요.");
            }
            districtId = jdbc.sql("SELECT id FROM district WHERE code = :code").param("code", approval.districtCode())
                    .query(Short.class).optional().orElseThrow(() -> ApiException.badRequest("알 수 없는 구 코드예요."));
        } else {
            place = lookup.find(report.name(), report.address())
                    .orElseThrow(() -> ApiException.badRequest("카카오에서 위치를 찾지 못했어요. 아래에서 직접 검색해서 고르거나, 좌표를 직접 넣어 주세요."));
            lat = place.lat();
            lng = place.lng();
            roadAddress = blankToNull(place.roadAddress());
            jibunAddress = blankToNull(place.jibunAddress());
            phone = blankToNull(place.phone());
            kakaoPlaceId = blankToNull(place.kakaoPlaceId());
            districtId = districtOf(place.jibunAddress() != null && !place.jibunAddress().isBlank() ? place.jibunAddress() : place.roadAddress());
            if (districtId == null) {
                throw ApiException.badRequest("대전 동구·중구·서구·유성구·대덕구가 아니에요. (" + place.jibunAddress() + ")");
            }
            if (kakaoPlaceId != null && jdbc.sql("SELECT EXISTS (SELECT 1 FROM bakery WHERE kakao_place_id = :id)")
                    .param("id", kakaoPlaceId).query(Boolean.class).single()) {
                throw ApiException.conflict("이미 등록된 빵집이에요. (카카오 장소 " + kakaoPlaceId + ")");
            }
        }
        return jdbc.sql("""
                        INSERT INTO bakery (name, district_id, road_address, jibun_address, location, phone, kakao_place_id,
                                            instagram_username, status)
                        VALUES (:name, :district, :road, :jibun, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), :phone, :kakao,
                                :instagram, 'ACTIVE')
                        RETURNING id
                        """)
                .param("name", report.name()).param("district", districtId).param("road", roadAddress)
                .param("jibun", jibunAddress).param("lng", lng).param("lat", lat).param("phone", phone)
                .param("kakao", kakaoPlaceId).param("instagram", report.instagram())
                .query(Long.class).single();
    }

    /** 주소("대전 유성구 …")에서 구 ID. 대전 5개 구가 아니면 null. */
    private Short districtOf(String address) {
        if (address == null) {
            return null;
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length < 2 || !tokens[0].startsWith("대전")) {
            return null;
        }
        return jdbc.sql("SELECT id FROM district WHERE name = :name").param("name", tokens[1]).query(Short.class).optional().orElse(null);
    }

    private record Pending(String kind, String name, String address, String instagram, Long bakeryId) {
    }

    /** 처리 중에 다른 운영자가 같은 제보를 처리하지 못하게 잠그고, 확인 중(PENDING)인지 본다. */
    private Pending lockPending(long reportId) {
        Optional<String> status = jdbc.sql("SELECT status FROM bakery_report WHERE id = :id FOR UPDATE")
                .param("id", reportId).query(String.class).optional();
        if (status.isEmpty()) {
            throw ApiException.notFound("제보를 찾을 수 없어요.");
        }
        if (!"PENDING".equals(status.get())) {
            throw ApiException.conflict("이미 처리된 제보예요.");
        }
        return jdbc.sql("SELECT kind, name, address, instagram_username, bakery_id FROM bakery_report WHERE id = :id")
                .param("id", reportId)
                .query((rs, n) -> new Pending(rs.getString("kind"), rs.getString("name"), rs.getString("address"),
                        rs.getString("instagram_username"), (Long) rs.getObject("bakery_id")))
                .single();
    }

    private void requireBakery(Long bakeryId) {
        if (bakeryId == null || !jdbc.sql("SELECT EXISTS (SELECT 1 FROM bakery WHERE id = :id)").param("id", bakeryId)
                .query(Boolean.class).single()) {
            throw ApiException.notFound("대상 빵집이 없어졌어요. 반려해 주세요.");
        }
    }

    private Row one(long id) {
        return jdbc.sql("""
                        SELECT r.id, r.kind, r.status, r.name, r.address, r.instagram_username, r.note, r.admin_note,
                               r.bakery_id, b.name AS bakery_name, b.status AS bakery_status,
                               u.id AS reporter_id, u.nickname, r.created_at, r.reviewed_at
                          FROM bakery_report r JOIN app_user u ON u.id = r.user_id LEFT JOIN bakery b ON b.id = r.bakery_id
                         WHERE r.id = :id
                        """)
                .param("id", id)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("kind"), rs.getString("status"),
                        rs.getString("name"), rs.getString("address"), rs.getString("instagram_username"),
                        rs.getString("note"), rs.getString("admin_note"), (Long) rs.getObject("bakery_id"),
                        rs.getString("bakery_name"), rs.getString("bakery_status"), rs.getLong("reporter_id"),
                        rs.getString("nickname"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("reviewed_at", OffsetDateTime.class)))
                .single();
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        if (text.length() > max) {
            throw ApiException.badRequest("안내 문구는 " + max + "자까지 쓸 수 있어요.");
        }
        return text;
    }

    private static void requireMax(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw ApiException.badRequest(label + " 값이 너무 길어요.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
