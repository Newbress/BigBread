package com.bigbread.api.bakery;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 PostGIS DB가 필요하다 (SchemaIntegrationTest와 동일). 각 테스트는 롤백된다.
 * 다른 데이터(개발 시드 등)와 섞이지 않도록 대전 밖의 가상 기준점(36.0, 127.0) 주변에만 데이터를 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BakeryApiTest {

    private static final double BASE_LAT = 36.0;
    private static final double BASE_LNG = 127.0;
    /** 위도 0.0045도 ≈ 500m */
    private static final double HALF_KM = 0.0045;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long bakeryA;
    private long bakeryB;

    @BeforeEach
    void setUp() {
        // A: 기준점(서구, 인기 30), B: 북쪽 500m(유성구, 인기 90), C: 북쪽 3km(서구, 인기 60), H: 숨김
        bakeryA = insert("테스트A", 3, BASE_LAT, BASE_LNG, 30, "ACTIVE");
        bakeryB = insert("테스트B", 4, BASE_LAT + HALF_KM, BASE_LNG, 90, "ACTIVE");
        insert("테스트C", 3, BASE_LAT + HALF_KM * 6, BASE_LNG, 60, "ACTIVE");
        insert("테스트숨김", 3, BASE_LAT, BASE_LNG, 99, "HIDDEN");

        jdbc.update("UPDATE bakery SET instagram_username = 'test_bakery', kakao_place_id = '12345', "
                + "opening_hours = '{\"mon\":\"08:00-20:00\"}'::jsonb WHERE id = ?", bakeryB);
        jdbc.update("INSERT INTO bread (bakery_id, name, price, signature) VALUES (?, '소금빵', 3500, TRUE)", bakeryB);
        jdbc.update("INSERT INTO bread (bakery_id, name, price, signature) VALUES (?, '식빵', 5000, FALSE)", bakeryB);
        jdbc.update("INSERT INTO bread (bakery_id, name, price, signature) VALUES (?, '튀소', 4000, TRUE)", bakeryB);
        jdbc.update("INSERT INTO source_link (bakery_id, platform, url, title, published_at) VALUES "
                + "(?, 'NAVER_BLOG', 'https://example.com/old', '예전 글', now() - interval '10 days')", bakeryB);
        jdbc.update("INSERT INTO source_link (bakery_id, platform, url, title, published_at) VALUES "
                + "(?, 'INSTAGRAM', 'https://example.com/new', '최신 글', now())", bakeryB);
    }

    @Test
    void 반경_1km_거리순이면_가까운_순서로_거리와_함께_내려온다() throws Exception {
        mvc.perform(get("/api/bakeries")
                        .param("lat", "36.0").param("lng", "127.0").param("radius", "1000").param("sort", "DISTANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("테스트A", "테스트B")))
                .andExpect(jsonPath("$.items[0].distanceMeters", closeTo(0.0, 1.0)))
                .andExpect(jsonPath("$.items[1].distanceMeters", closeTo(500.0, 5.0)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void 반경이_넓으면_더_많이_잡히고_숨김_빵집은_제외된다() throws Exception {
        mvc.perform(get("/api/bakeries")
                        .param("lat", "36.0").param("lng", "127.0").param("radius", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("테스트A", "테스트B", "테스트C")));
    }

    @Test
    void 기본_정렬은_인기순이다() throws Exception {
        mvc.perform(get("/api/bakeries")
                        .param("lat", "36.0").param("lng", "127.0").param("radius", "5000"))
                .andExpect(jsonPath("$.items[*].name", contains("테스트B", "테스트C", "테스트A")));
    }

    @Test
    void 구_코드로_필터링하고_반경과_함께_쓸_수_있다() throws Exception {
        mvc.perform(get("/api/bakeries")
                        .param("district", "yuseong")
                        .param("lat", "36.0").param("lng", "127.0").param("radius", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("테스트B"))
                .andExpect(jsonPath("$.items[0].district.name").value("유성구"));
    }

    @Test
    void 위치가_없으면_distance는_null이다() throws Exception {
        mvc.perform(get("/api/bakeries").param("district", "YUSEONG").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name=='테스트B')].distanceMeters").value(contains(nullValue())));
    }

    @Test
    void 페이지네이션이_동작한다() throws Exception {
        mvc.perform(get("/api/bakeries")
                        .param("lat", "36.0").param("lng", "127.0").param("radius", "5000")
                        .param("sort", "DISTANCE").param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("테스트B"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void 요약에_대표_빵이_최대_3개_포함된다() throws Exception {
        mvc.perform(get("/api/bakeries")
                        .param("lat", "36.0").param("lng", "127.0").param("radius", "1000"))
                .andExpect(jsonPath("$.items[?(@.name=='테스트B')].signatureBreads[*]")
                        .value(contains("소금빵", "튀소")));
    }

    @Test
    void 잘못된_요청은_400이다() throws Exception {
        mvc.perform(get("/api/bakeries").param("sort", "DISTANCE")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bakeries").param("lat", "36.0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bakeries").param("radius", "1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bakeries").param("lat", "99").param("lng", "127")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bakeries").param("district", "NOWHERE")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bakeries").param("size", "1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bakeries").param("sort", "UNKNOWN")).andExpect(status().isBadRequest());
    }

    @Test
    void 상세는_빵_원본링크_외부링크를_포함한다() throws Exception {
        mvc.perform(get("/api/bakeries/{id}", bakeryB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("테스트B"))
                .andExpect(jsonPath("$.district.code").value("YUSEONG"))
                .andExpect(jsonPath("$.openingHours.mon").value("08:00-20:00"))
                .andExpect(jsonPath("$.links.instagram").value("https://www.instagram.com/test_bakery/"))
                .andExpect(jsonPath("$.links.kakaoMap").value("https://place.map.kakao.com/12345"))
                .andExpect(jsonPath("$.breads[*].name", contains("소금빵", "튀소", "식빵")))
                .andExpect(jsonPath("$.breads[0].signature").value(true))
                .andExpect(jsonPath("$.sourceLinks[*].title", contains("최신 글", "예전 글")));
    }

    @Test
    void 정보가_없는_필드는_null이고_빈_목록으로_내려온다() throws Exception {
        mvc.perform(get("/api/bakeries/{id}", bakeryA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingHours").value(nullValue()))
                .andExpect(jsonPath("$.links.instagram").value(nullValue()))
                .andExpect(jsonPath("$.breads", hasSize(0)))
                .andExpect(jsonPath("$.sourceLinks", hasSize(0)));
    }

    @Test
    void 없거나_숨김인_빵집_상세는_404다() throws Exception {
        mvc.perform(get("/api/bakeries/{id}", 999999999L)).andExpect(status().isNotFound());
        Long hidden = jdbc.queryForObject("SELECT id FROM bakery WHERE name = '테스트숨김'", Long.class);
        mvc.perform(get("/api/bakeries/{id}", hidden)).andExpect(status().isNotFound());
    }

    private long insert(String name, int districtId, double lat, double lng, double score, String status) {
        Long id = jdbc.queryForObject("""
                INSERT INTO bakery (name, district_id, location, popularity_score, status)
                VALUES (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?) RETURNING id
                """, Long.class, name, districtId, lng, lat, score, status);
        return id;
    }
}
