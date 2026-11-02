package com.bigbread.api.admin;

import com.bigbread.api.bakery.ExternalLinks;
import com.bigbread.api.common.ApiException;
import com.bigbread.api.common.PageResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 빵집 관리: 검색, 숨김·표시·폐업, 인스타 계정·영업시간·전화번호 수정. 숨김 처리된 빵집도 볼 수 있다. */
@Service
public class AdminBakeryService {

    public record Row(long id, String name, String districtName, String roadAddress, String status, String instagramUsername,
                      boolean hasOpeningHours, int reviewCount, Double ratingAvg, double popularityScore,
                      String kakaoPlaceId, OffsetDateTime updatedAt) {
    }

    public record Detail(Row summary, String jibunAddress, String phone, Object openingHours, double lat, double lng,
                         String kakaoMapUrl, String naverMapUrl) {
    }

    static final Set<String> STATUSES = Set.of("ACTIVE", "HIDDEN", "CLOSED", "PENDING");
    private static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    private static final Pattern RANGE = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d-([01]\\d|2[0-3]):[0-5]\\d");
    private static final Pattern INSTAGRAM = Pattern.compile("[a-z0-9._]{1,30}");
    private static final Pattern PHONE = Pattern.compile("[0-9+() -]{5,30}");

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final AdminAudit audit;

    public AdminBakeryService(JdbcClient jdbc, JsonMapper json, AdminAudit audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    private static final String SELECT = """
            SELECT b.id, b.name, d.name AS district_name, b.road_address, b.jibun_address, b.status, b.instagram_username,
                   b.opening_hours IS NOT NULL AS has_hours, b.review_count, b.rating_avg, b.popularity_score, b.kakao_place_id,
                   b.updated_at, b.phone, b.opening_hours::text AS hours, ST_Y(b.location) AS lat, ST_X(b.location) AS lng
              FROM bakery b JOIN district d ON d.id = b.district_id
            """;

    public PageResponse<Row> search(String q, String status, int page, int size) {
        if (status != null && !STATUSES.contains(status)) {
            throw ApiException.badRequest("알 수 없는 상태예요: " + status);
        }
        String text = q == null || q.isBlank() ? null : "%" + q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String where = """
                 WHERE (CAST(:status AS varchar) IS NULL OR b.status = :status)
                   AND (CAST(:q AS varchar) IS NULL OR b.name ILIKE :q OR b.road_address ILIKE :q OR b.jibun_address ILIKE :q
                        OR b.instagram_username ILIKE :q)
                """;
        long total = jdbc.sql("SELECT count(*) FROM bakery b" + where).param("status", status).param("q", text)
                .query(Long.class).single();
        List<Row> items = jdbc.sql(SELECT + where + " ORDER BY lower(b.name) COLLATE \"C\", b.id LIMIT :limit OFFSET :offset")
                .param("status", status).param("q", text).param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> row(rs)).list();
        return PageResponse.of(items, page, size, total);
    }

    public Detail get(long id) {
        return jdbc.sql(SELECT + " WHERE b.id = :id").param("id", id)
                .query((rs, n) -> {
                    String hours = rs.getString("hours");
                    String jibun = rs.getString("jibun_address");
                    String road = rs.getString("road_address");
                    String kakao = rs.getString("kakao_place_id");
                    return new Detail(row(rs), jibun, rs.getString("phone"),
                            hours == null ? null : json.readValue(hours, Object.class), rs.getDouble("lat"), rs.getDouble("lng"),
                            kakao == null ? null : "https://place.map.kakao.com/" + kakao,
                            ExternalLinks.naverMapSearch(rs.getString("name"), road, jibun));
                })
                .optional().orElseThrow(() -> ApiException.notFound("빵집을 찾을 수 없어요."));
    }

    /**
     * 보낸 필드만 바꾼다 (값을 null로 보내면 비운다). 바꿀 수 있는 것: status, instagramUsername, openingHours, phone.
     * openingHours는 {"mon":"08:00-21:00", …, "sun":"closed"} 형태다.
     */
    @Transactional
    public Detail update(long adminId, long id, Map<String, Object> body) {
        Detail before = get(id);
        Set<String> allowed = Set.of("status", "instagramUsername", "openingHours", "phone");
        for (String key : body.keySet()) {
            if (!allowed.contains(key)) {
                throw ApiException.badRequest("바꿀 수 없는 항목이에요: " + key);
            }
        }
        if (body.isEmpty()) {
            throw ApiException.badRequest("바꿀 내용이 없어요.");
        }
        List<String> sets = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> changes = new ArrayList<>();
        if (body.containsKey("status")) {
            String status = String.valueOf(body.get("status"));
            if (!STATUSES.contains(status)) {
                throw ApiException.badRequest("상태는 ACTIVE, HIDDEN, CLOSED, PENDING 중 하나예요.");
            }
            sets.add("status = :status");
            params.put("status", status);
            changes.add("상태 " + before.summary().status() + " → " + status);
        }
        if (body.containsKey("instagramUsername")) {
            String instagram = cleanInstagram(body.get("instagramUsername"));
            sets.add("instagram_username = :instagram");
            params.put("instagram", instagram);
            changes.add("인스타 " + before.summary().instagramUsername() + " → " + instagram);
        }
        if (body.containsKey("phone")) {
            Object raw = body.get("phone");
            String phone = raw == null || String.valueOf(raw).isBlank() ? null : String.valueOf(raw).strip();
            if (phone != null && !PHONE.matcher(phone).matches()) {
                throw ApiException.badRequest("전화번호 형식이 올바르지 않아요.");
            }
            sets.add("phone = :phone");
            params.put("phone", phone);
            changes.add("전화번호 " + before.phone() + " → " + phone);
        }
        if (body.containsKey("openingHours")) {
            String hours = cleanHours(body.get("openingHours"));
            sets.add("opening_hours = CAST(:hours AS jsonb)");
            params.put("hours", hours);
            changes.add("영업시간 수정" + (hours == null ? "(삭제)" : ""));
        }
        var statement = jdbc.sql("UPDATE bakery SET " + String.join(", ", sets) + ", updated_at = now() WHERE id = :id").param("id", id);
        for (var entry : params.entrySet()) {
            statement = statement.param(entry.getKey(), entry.getValue());
        }
        statement.update();
        audit.record(adminId, "BAKERY_UPDATE", "BAKERY", id, before.summary().name() + ": " + String.join(", ", changes));
        return get(id);
    }

    private static String cleanInstagram(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        String value = String.valueOf(raw).strip();
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        value = value.toLowerCase(java.util.Locale.ROOT);
        if (!INSTAGRAM.matcher(value).matches()) {
            throw ApiException.badRequest("인스타그램 계정은 영문·숫자·밑줄(_)·점(.)만 쓸 수 있어요.");
        }
        return value;
    }

    /** 요일별 "HH:MM-HH:MM" 또는 "closed". 비어 있거나 null이면 영업시간을 지운다. */
    private String cleanHours(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw ApiException.badRequest("영업시간 형식이 올바르지 않아요.");
        }
        if (map.isEmpty()) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String day : DAYS) {
            Object value = map.get(day);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).strip();
            if (!text.equalsIgnoreCase("closed") && !RANGE.matcher(text).matches()) {
                throw ApiException.badRequest("영업시간은 '09:00-21:00' 또는 'closed' 형태로 적어 주세요. (" + day + ")");
            }
            result.put(day, text.equalsIgnoreCase("closed") ? "closed" : text);
        }
        for (Object key : map.keySet()) {
            if (!DAYS.contains(String.valueOf(key))) {
                throw ApiException.badRequest("알 수 없는 요일이에요: " + key);
            }
        }
        if (result.isEmpty()) {
            return null;
        }
        return json.writeValueAsString(result);
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.math.BigDecimal avg = rs.getBigDecimal("rating_avg");
        return new Row(rs.getLong("id"), rs.getString("name"), rs.getString("district_name"), rs.getString("road_address"),
                rs.getString("status"), rs.getString("instagram_username"), rs.getBoolean("has_hours"), rs.getInt("review_count"),
                avg == null ? null : avg.doubleValue(), rs.getDouble("popularity_score"), rs.getString("kakao_place_id"),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
