package com.bigbread.api.user;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class FavoriteApiTest extends UserApiTestSupport {

    @Test
    void 비로그인은_조회도_변경도_401이다() throws Exception {
        long bakery = newBakery("즐겨찾기A", "ACTIVE");
        mvc.perform(get("/api/my/favorites")).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf(put("/api/my/favorites/{id}", bakery))).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf(delete("/api/my/favorites/{id}", bakery))).andExpect(status().isUnauthorized());
    }

    @Test
    void 담으면_최근에_담은_순서로_조회된다() throws Exception {
        long me = newUser("나");
        long a = newBakery("즐겨찾기A", "ACTIVE");
        long b = newBakery("즐겨찾기B", "ACTIVE");

        mvc.perform(withCsrf(put("/api/my/favorites/{id}", a)).with(as(me))).andExpect(status().isNoContent());
        mvc.perform(withCsrf(put("/api/my/favorites/{id}", b)).with(as(me))).andExpect(status().isNoContent());

        mvc.perform(get("/api/my/favorites").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].bakery.name", contains("즐겨찾기B", "즐겨찾기A")))
                .andExpect(jsonPath("$[0].bakery.id").value(b))
                .andExpect(jsonPath("$[0].favoritedAt").exists());
    }

    @Test
    void 같은_빵집을_다시_담거나_없는_걸_빼도_오류_없이_끝난다() throws Exception {
        long me = newUser("나");
        long a = newBakery("즐겨찾기A", "ACTIVE");

        mvc.perform(withCsrf(put("/api/my/favorites/{id}", a)).with(as(me))).andExpect(status().isNoContent());
        mvc.perform(withCsrf(put("/api/my/favorites/{id}", a)).with(as(me))).andExpect(status().isNoContent());
        mvc.perform(get("/api/my/favorites").with(as(me))).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(withCsrf(delete("/api/my/favorites/{id}", a)).with(as(me))).andExpect(status().isNoContent());
        mvc.perform(withCsrf(delete("/api/my/favorites/{id}", a)).with(as(me))).andExpect(status().isNoContent());
        mvc.perform(get("/api/my/favorites").with(as(me))).andExpect(jsonPath("$", empty()));
    }

    @Test
    void 없거나_숨김인_빵집은_담을_수_없다() throws Exception {
        long me = newUser("나");
        long hidden = newBakery("숨김빵집", "HIDDEN");

        mvc.perform(withCsrf(put("/api/my/favorites/{id}", hidden)).with(as(me))).andExpect(status().isNotFound());
        mvc.perform(withCsrf(put("/api/my/favorites/{id}", 999_999_999L)).with(as(me))).andExpect(status().isNotFound());
    }

    @Test
    void 담은_뒤_숨김_처리된_빵집은_목록에서_빠진다() throws Exception {
        long me = newUser("나");
        long a = newBakery("보이는빵집", "ACTIVE");
        long b = newBakery("나중에숨김", "ACTIVE");
        mvc.perform(withCsrf(put("/api/my/favorites/{id}", a)).with(as(me)));
        mvc.perform(withCsrf(put("/api/my/favorites/{id}", b)).with(as(me)));

        jdbc.update("UPDATE bakery SET status = 'HIDDEN' WHERE id = ?", b);

        mvc.perform(get("/api/my/favorites").with(as(me)))
                .andExpect(jsonPath("$[*].bakery.name", contains("보이는빵집")));
    }

    @Test
    void 사용자마다_즐겨찾기가_따로다() throws Exception {
        long me = newUser("나");
        long other = newUser("남");
        long a = newBakery("즐겨찾기A", "ACTIVE");
        mvc.perform(withCsrf(put("/api/my/favorites/{id}", a)).with(as(me)));

        mvc.perform(get("/api/my/favorites").with(as(other))).andExpect(jsonPath("$", empty()));
        // 남이 빼도 내 것은 그대로다
        mvc.perform(withCsrf(delete("/api/my/favorites/{id}", a)).with(as(other))).andExpect(status().isNoContent());
        mvc.perform(get("/api/my/favorites").with(as(me))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void CSRF_토큰이_없는_변경_요청은_403이다() throws Exception {
        long me = newUser("나");
        long a = newBakery("즐겨찾기A", "ACTIVE");
        mvc.perform(put("/api/my/favorites/{id}", a).with(as(me))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/my/favorites/{id}", a).with(as(me))).andExpect(status().isForbidden());
    }

    @Test
    void 최대_개수를_넘으면_400이고_이미_담은_것은_계속_다룰_수_있다() throws Exception {
        long me = newUser("나");
        jdbc.update("""
                INSERT INTO bakery (name, district_id, location, popularity_score, status)
                SELECT '대량-' || g, 3, ST_SetSRID(ST_MakePoint(127.0, 36.0), 4326), 1, 'ACTIVE'
                  FROM generate_series(1, ?) g
                """, FavoriteService.MAX_FAVORITES + 1);
        jdbc.update("""
                INSERT INTO favorite (user_id, bakery_id)
                SELECT ?, id FROM bakery WHERE name LIKE '대량-%' ORDER BY id LIMIT ?
                """, me, FavoriteService.MAX_FAVORITES);
        Long last = jdbc.queryForObject("SELECT id FROM bakery WHERE name = ?", Long.class, "대량-" + (FavoriteService.MAX_FAVORITES + 1));
        Long already = jdbc.queryForObject("SELECT id FROM bakery WHERE name = '대량-1'", Long.class);

        mvc.perform(withCsrf(put("/api/my/favorites/{id}", last)).with(as(me))).andExpect(status().isBadRequest());
        mvc.perform(withCsrf(put("/api/my/favorites/{id}", already)).with(as(me))).andExpect(status().isNoContent());
    }
}
