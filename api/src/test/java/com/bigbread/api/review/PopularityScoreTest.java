package com.bigbread.api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bigbread.api.user.UserApiTestSupport;
import org.junit.jupiter.api.Test;

/** 인기 점수 = 즐겨찾기 x 2 + 코스 수 + 리뷰 수 x 보정 평균 평점. 활동이 바뀌면 같은 요청 안에서 다시 계산된다. */
class PopularityScoreTest extends UserApiTestSupport {

    private double score(long bakery) {
        return jdbc.queryForObject("SELECT popularity_score FROM bakery WHERE id = ?", Double.class, bakery);
    }

    @Test
    void 즐겨찾기는_2점이고_빼면_되돌아간다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("인기A", "ACTIVE");
        mvc.perform(withCsrf(put("/api/my/favorites/{id}", bakery)).with(as(me))).andExpect(status().isNoContent());
        assertThat(score(bakery)).isEqualTo(2.0);
        mvc.perform(withCsrf(delete("/api/my/favorites/{id}", bakery)).with(as(me))).andExpect(status().isNoContent());
        assertThat(score(bakery)).isEqualTo(0.0);
    }

    @Test
    void 리뷰는_보정_평균_평점만큼_점수를_주고_평균과_개수가_채워진다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("인기A", "ACTIVE");
        mvc.perform(withCsrf(put("/api/my/reviews/{id}", bakery)).with(as(me)).contentType(APPLICATION_JSON)
                .content("{\"rating\":5}")).andExpect(status().isOk());
        // 보정 평균 (3 x 3.5 + 5) / (3 + 1) = 3.875
        assertThat(score(bakery)).isEqualTo(3.88);
        mvc.perform(get("/api/bakeries/{id}", bakery))
                .andExpect(jsonPath("$.ratingAvg").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));

        mvc.perform(withCsrf(delete("/api/my/reviews/{id}", bakery)).with(as(me))).andExpect(status().isNoContent());
        assertThat(score(bakery)).isEqualTo(0.0);
        mvc.perform(get("/api/bakeries/{id}", bakery))
                .andExpect(jsonPath("$.ratingAvg").doesNotExist())
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void 코스에_담기면_1점이고_코스를_지우면_빠진다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("인기A", "ACTIVE");
        String body = "{\"title\":\"코스\",\"order\":\"MANUAL\",\"bakeryIds\":[" + bakery + "]}";
        mvc.perform(withCsrf(post("/api/my/courses")).with(as(me)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        assertThat(score(bakery)).isEqualTo(1.0);
        long courseId = jdbc.queryForObject("SELECT id FROM course WHERE user_id = ?", Long.class, me);
        mvc.perform(withCsrf(delete("/api/my/courses/{id}", courseId)).with(as(me))).andExpect(status().isNoContent());
        assertThat(score(bakery)).isEqualTo(0.0);
    }
}
