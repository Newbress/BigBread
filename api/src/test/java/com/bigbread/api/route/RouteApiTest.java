package com.bigbread.api.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 PostGIS DB가 필요하다. 각 테스트는 롤백된다.
 * 대전 밖 가상 기준점(36.0, 127.0)을 남북으로 놓고 빵집을 만든다: 위도 0.0045도 ≈ 500m.
 *
 * <pre>
 *   A  (0m,   인기 30) ── B (500m, 인기 90) ── ... ── C (3km, 인기 60)
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RouteApiTest {

    private static final double LNG = 127.0;
    private static final double HALF_KM = 0.0045;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private long a;
    private long b;
    private long c;
    private long hidden;

    @BeforeEach
    void setUp() {
        a = insert("동선A", 36.0, 30, "ACTIVE");
        b = insert("동선B", 36.0 + HALF_KM, 90, "ACTIVE");
        c = insert("동선C", 36.0 + HALF_KM * 6, 60, "ACTIVE");
        hidden = insert("동선숨김", 36.0, 99, "HIDDEN");
    }

    @Test
    void 남쪽에서_출발하면_가까운_쪽부터_A_B_C_순서다() throws Exception {
        // 요청 순서는 일부러 섞는다
        plan("[%d,%d,%d]".formatted(c, a, b), "{\"lat\":35.999,\"lng\":127.0}", "NEAREST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order").value("NEAREST"))
                .andExpect(jsonPath("$.stops[*].bakery.name", contains("동선A", "동선B", "동선C")))
                .andExpect(jsonPath("$.stops[*].seq", contains(1, 2, 3)));
    }

    @Test
    void 북쪽에서_출발하면_C_B_A_순서다() throws Exception {
        plan("[%d,%d,%d]".formatted(a, b, c), "{\"lat\":36.05,\"lng\":127.0}", "NEAREST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[*].bakery.name", contains("동선C", "동선B", "동선A")));
    }

    @Test
    void 순서_기준을_생략하면_최단_동선이다() throws Exception {
        plan("[%d,%d,%d]".formatted(c, a, b), "{\"lat\":35.999,\"lng\":127.0}", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order").value("NEAREST"))
                .andExpect(jsonPath("$.stops[*].bakery.name", contains("동선A", "동선B", "동선C")));
    }

    @Test
    void 인기순이면_출발지와_상관없이_점수가_높은_순서다() throws Exception {
        plan("[%d,%d,%d]".formatted(a, b, c), "{\"lat\":35.999,\"lng\":127.0}", "POPULARITY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[*].bakery.name", contains("동선B", "동선C", "동선A")));
    }

    @Test
    void 직접_정렬이면_요청_순서를_그대로_유지한다() throws Exception {
        plan("[%d,%d,%d]".formatted(c, a, b), "{\"lat\":35.999,\"lng\":127.0}", "MANUAL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[*].bakery.name", contains("동선C", "동선A", "동선B")));
    }

    @Test
    void 구간_거리의_합이_총거리와_같고_경로_좌표는_출발지를_포함한다() throws Exception {
        String body = plan("[%d,%d,%d]".formatted(a, b, c), "{\"lat\":35.999,\"lng\":127.0}", "NEAREST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimated").value(true))
                .andExpect(jsonPath("$.path", hasSize(4))) // 출발지 + 정류장 3개
                .andExpect(jsonPath("$.stops[0].leg.kakaoWalkUrl", startsWith("https://map.kakao.com/link/by/walk/")))
                .andReturn().getResponse().getContentAsString();

        List<Double> legs = JsonPath.read(body, "$.stops[*].leg.distanceMeters");
        List<Integer> minutes = JsonPath.read(body, "$.stops[*].leg.durationMinutes");
        double total = ((Number) JsonPath.read(body, "$.totalDistanceMeters")).doubleValue();
        int totalMinutes = ((Number) JsonPath.read(body, "$.totalDurationMinutes")).intValue();

        assertThat(legs).hasSize(3);
        assertThat(legs.stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(total, within(0.2));
        assertThat(minutes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(totalMinutes);
        assertThat(legs.get(1)).isCloseTo(500, within(5.0)); // A → B
        assertThat(legs.get(2)).isCloseTo(2500, within(25.0)); // B → C
    }

    @Test
    void 출발지가_없으면_첫_정류장은_구간이_없고_총거리는_정류장_사이만_더한다() throws Exception {
        plan("[%d,%d,%d]".formatted(c, a, b), null, "NEAREST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value(nullValue()))
                .andExpect(jsonPath("$.stops[0].leg").value(nullValue()))
                .andExpect(jsonPath("$.stops[1].leg.distanceMeters").exists())
                .andExpect(jsonPath("$.path", hasSize(3)));
    }

    @Test
    void 빵집_하나와_출발지면_구간이_하나다() throws Exception {
        plan("[%d]".formatted(b), "{\"lat\":36.0,\"lng\":127.0}", "NEAREST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops", hasSize(1)))
                .andExpect(jsonPath("$.stops[0].leg.distanceMeters").exists())
                .andExpect(jsonPath("$.path", hasSize(2)));
    }

    @Test
    void 잘못된_요청은_400이다() throws Exception {
        String ids = "[%d,%d]".formatted(a, b);
        String start = "{\"lat\":36.0,\"lng\":127.0}";

        plan("[]", start, "NEAREST").andExpect(status().isBadRequest());
        plan("[1,2,3,4,5,6,7,8,9,10,11]", start, "NEAREST").andExpect(status().isBadRequest());
        plan("[%d,%d]".formatted(a, a), start, "NEAREST").andExpect(status().isBadRequest());
        plan("[0]", start, "NEAREST").andExpect(status().isBadRequest());
        plan(ids, "{\"lat\":99,\"lng\":127.0}", "NEAREST").andExpect(status().isBadRequest());
        plan(ids, "{\"lat\":36.0}", "NEAREST").andExpect(status().isBadRequest());
        plan(ids, start, "FASTEST").andExpect(status().isBadRequest());
        mvc.perform(post("/api/routes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/routes").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없거나_숨김인_빵집이_섞이면_400이고_메시지에_ID가_들어간다() throws Exception {
        plan("[%d,999999999]".formatted(a), null, "NEAREST")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("999999999")));
        plan("[%d,%d]".formatted(a, hidden), null, "NEAREST").andExpect(status().isBadRequest());
    }

    private ResultActions plan(String bakeryIds, String start, String order) throws Exception {
        StringBuilder json = new StringBuilder("{\"bakeryIds\":").append(bakeryIds);
        if (start != null) {
            json.append(",\"start\":").append(start);
        }
        if (order != null) {
            json.append(",\"order\":\"").append(order).append('"');
        }
        json.append('}');
        return mvc.perform(post("/api/routes").contentType(MediaType.APPLICATION_JSON).content(json.toString()));
    }

    private long insert(String name, double lat, double score, String status) {
        Long id = jdbc.queryForObject("""
                INSERT INTO bakery (name, district_id, location, popularity_score, status)
                VALUES (?, 3, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?) RETURNING id
                """, Long.class, name, LNG, lat, score, status);
        return id;
    }
}
