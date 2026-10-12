package com.bigbread.api.bakery;

import com.bigbread.api.bakery.BakerySummary.DistrictInfo;
import com.bigbread.api.common.ApiException;
import com.bigbread.api.common.PageResponse;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 빵집 조회 전용 서비스. 반경/거리 계산은 PostGIS 함수를 직접 써야 해서 JdbcClient + 네이티브 SQL을 사용한다.
 * 반경 검색은 {@code idx_bakery_location_geog} (location::geography GiST 인덱스)를 탄다.
 */
@Service
@Transactional(readOnly = true)
public class BakeryQueryService {

    private static final String POINT = "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography";

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    public BakeryQueryService(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    public PageResponse<BakerySummary> search(BakerySearchCondition c) {
        if (c.district() != null) {
            boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM district WHERE code = :code)")
                    .param("code", c.district())
                    .query(Boolean.class)
                    .single();
            if (!exists) {
                throw ApiException.badRequest("알 수 없는 구 코드입니다: " + c.district());
            }
        }

        Map<String, Object> params = new HashMap<>();
        StringBuilder from = new StringBuilder(
                " FROM bakery b JOIN district d ON d.id = b.district_id WHERE b.status = 'ACTIVE'");
        if (c.district() != null) {
            from.append(" AND d.code = :district");
            params.put("district", c.district());
        }
        if (c.hasLocation()) {
            params.put("lat", c.lat());
            params.put("lng", c.lng());
            if (c.radius() != null) {
                from.append(" AND ST_DWithin(b.location::geography, ").append(POINT).append(", :radius)");
                params.put("radius", c.radius());
            }
        }

        long total = jdbc.sql("SELECT count(*)" + from)
                .params(params)
                .query(Long.class)
                .single();

        String distance = c.hasLocation()
                ? "ST_Distance(b.location::geography, " + POINT + ")"
                : "NULL::double precision";
        String orderBy = switch (c.sort()) {
            case POPULARITY -> "b.popularity_score DESC, b.id";
            case DISTANCE -> "distance_m, b.id";
            case NAME -> "b.name, b.id";
        };
        String select = """
                SELECT b.id, b.name, d.id AS district_id, d.code AS district_code, d.name AS district_name,
                       b.road_address, ST_Y(b.location) AS lat, ST_X(b.location) AS lng,
                       b.popularity_score,
                       ARRAY(SELECT br.name FROM bread br
                              WHERE br.bakery_id = b.id AND br.signature ORDER BY br.id LIMIT 3) AS signature_breads,
                       %s AS distance_m
                """.formatted(distance);

        params.put("limit", c.size());
        params.put("offset", c.offset());
        List<BakerySummary> items = jdbc.sql(select + from + " ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> toSummary(rs))
                .list();

        return PageResponse.of(items, c.page(), c.size(), total);
    }

    public BakeryDetail get(long id) {
        BakeryDetail head = jdbc.sql("""
                        SELECT b.id, b.name, d.id AS district_id, d.code AS district_code, d.name AS district_name,
                               b.road_address, b.jibun_address, ST_Y(b.location) AS lat, ST_X(b.location) AS lng,
                               b.phone, b.opening_hours::text AS opening_hours, b.popularity_score,
                               b.instagram_username, b.kakao_place_id
                          FROM bakery b JOIN district d ON d.id = b.district_id
                         WHERE b.id = :id AND b.status = 'ACTIVE'
                        """)
                .param("id", id)
                .query((rs, n) -> toDetail(rs))
                .optional()
                .orElseThrow(() -> ApiException.notFound("빵집을 찾을 수 없습니다: " + id));

        List<BakeryDetail.Bread> breads = jdbc.sql("""
                        SELECT id, name, description, price, signature
                          FROM bread WHERE bakery_id = :id ORDER BY signature DESC, id
                        """)
                .param("id", id)
                .query((rs, n) -> new BakeryDetail.Bread(
                        rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                        (Integer) rs.getObject("price"), rs.getBoolean("signature")))
                .list();

        List<BakeryDetail.SourceLink> links = jdbc.sql("""
                        SELECT id, platform, url, title, summary, author_name, published_at
                          FROM source_link WHERE bakery_id = :id
                         ORDER BY published_at DESC NULLS LAST, id DESC LIMIT 20
                        """)
                .param("id", id)
                .query((rs, n) -> new BakeryDetail.SourceLink(
                        rs.getLong("id"), rs.getString("platform"), rs.getString("url"), rs.getString("title"),
                        rs.getString("summary"), rs.getString("author_name"),
                        rs.getObject("published_at", OffsetDateTime.class)))
                .list();

        return new BakeryDetail(head.id(), head.name(), head.district(), head.roadAddress(), head.jibunAddress(),
                head.lat(), head.lng(), head.phone(), head.openingHours(), head.popularityScore(), head.links(),
                breads, links);
    }

    private BakerySummary toSummary(ResultSet rs) throws SQLException {
        Double distance = (Double) rs.getObject("distance_m");
        return new BakerySummary(
                rs.getLong("id"), rs.getString("name"), district(rs), rs.getString("road_address"),
                rs.getDouble("lat"), rs.getDouble("lng"), rs.getDouble("popularity_score"),
                toList(rs.getArray("signature_breads")),
                distance == null ? null : Math.round(distance * 10) / 10.0);
    }

    private BakeryDetail toDetail(ResultSet rs) throws SQLException {
        String instagram = rs.getString("instagram_username");
        String kakaoPlaceId = rs.getString("kakao_place_id");
        BakeryDetail.Links links = new BakeryDetail.Links(
                instagram == null ? null : "https://www.instagram.com/" + instagram + "/",
                kakaoPlaceId == null ? null : "https://place.map.kakao.com/" + kakaoPlaceId);
        String hours = rs.getString("opening_hours");
        return new BakeryDetail(
                rs.getLong("id"), rs.getString("name"), district(rs), rs.getString("road_address"),
                rs.getString("jibun_address"), rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("phone"),
                hours == null ? null : jsonMapper.readValue(hours, Object.class),
                rs.getDouble("popularity_score"), links, List.of(), List.of());
    }

    private DistrictInfo district(ResultSet rs) throws SQLException {
        return new DistrictInfo(rs.getShort("district_id"), rs.getString("district_code"),
                rs.getString("district_name"));
    }

    private List<String> toList(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
