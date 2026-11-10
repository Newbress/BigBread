package com.bigbread.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class AdminUserApiTest extends AdminApiTestBase {

    @Test
    void 닉네임_번호로_검색하고_이메일은_가려서_보인다() throws Exception {
        long admin = newAdmin("운영자");
        long user = newUser("검색대상닉");
        jdbc.update("UPDATE app_user SET email = 'Secret.Mail@example.com' WHERE id = ?", user);

        adminGet(admin, "/api/admin/users?q=검색대상")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(user))
                .andExpect(jsonPath("$.items[0].maskedEmail").value("s**@example.com"))
                .andExpect(jsonPath("$.items[0].providers[0]").value("KAKAO"));
        adminGet(admin, "/api/admin/users?q=" + user).andExpect(jsonPath("$.items[0].id").value(user));
        // 이메일은 전체가 정확히 일치할 때만 찾는다 (일부 검색으로 이메일을 캐낼 수 없게)
        adminGet(admin, "/api/admin/users?q=secret.mail@example.com").andExpect(jsonPath("$.items", hasSize(1)));
        adminGet(admin, "/api/admin/users?q=secret.mail").andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 상세에는_쓴_글과_받은_신고_수가_나온다() throws Exception {
        long admin = newAdmin("운영자");
        long user = newUser("작성자");
        long bakery = newBakery("유저 빵집", "ACTIVE");
        long review = review(user, bakery, 4);
        report(newUser("신고1"), "REVIEW", review);

        adminGet(admin, "/api/admin/users/{id}", user)
                .andExpect(jsonPath("$.user.reviewCount").value(1))
                .andExpect(jsonPath("$.user.reportsReceived").value(1))
                .andExpect(jsonPath("$.recentReviews[0].bakeryName").value("유저 빵집"));
        adminGet(admin, "/api/admin/users/{id}", 999_999_999L).andExpect(status().isNotFound());
    }

    @Test
    void 정지하면_글은_못_쓰지만_조회와_로그인은_된다() throws Exception {
        long admin = newAdmin("운영자");
        long user = newUser("문제유저");
        long bakery = newBakery("정지 빵집", "ACTIVE");
        review(user, bakery, 4);

        adminPost(admin, "/api/admin/users/{id}/suspend", "{}", user).andExpect(status().isBadRequest());
        adminPost(admin, "/api/admin/users/{id}/suspend", "{\"reason\":\"도배\"}", user)
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.suspendedReason").value("도배"));
        adminPost(admin, "/api/admin/users/{id}/suspend", "{\"reason\":\"또\"}", user).andExpect(status().isConflict());

        // 쓰기는 막힌다 (사유 안내 포함)
        mvc.perform(withCsrf(put("/api/my/reviews/{id}", bakery)).with(as(user)).contentType(APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("도배")));
        mvc.perform(withCsrf(post("/api/my/bakery-reports")).with(as(user)).contentType(APPLICATION_JSON)
                .content("{\"kind\":\"NEW\",\"name\":\"가게\",\"address\":\"대전\"}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/my/reports")).with(as(user)).contentType(APPLICATION_JSON)
                .content("{\"targetType\":\"REVIEW\",\"targetId\":1,\"reason\":\"SPAM\"}")).andExpect(status().isForbidden());
        // 조회와 즐겨찾기, 내 리뷰 삭제는 그대로 된다
        mvc.perform(get("/api/my/reviews").with(as(user))).andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/my/favorites/{id}", bakery)).with(as(user))).andExpect(status().isNoContent());
        mvc.perform(withCsrf(delete("/api/my/reviews/{id}", bakery)).with(as(user))).andExpect(status().isNoContent());

        adminGet(admin, "/api/admin/users?suspended=true").andExpect(jsonPath("$.items[?(@.id==" + user + ")]", hasSize(1)));
        adminPost(admin, "/api/admin/users/{id}/unsuspend", "{}", user).andExpect(status().isOk());
        mvc.perform(withCsrf(put("/api/my/reviews/{id}", bakery)).with(as(user)).contentType(APPLICATION_JSON)
                .content("{\"rating\":5}")).andExpect(status().isOk());
        adminPost(admin, "/api/admin/users/{id}/unsuspend", "{}", user).andExpect(status().isConflict());
    }

    @Test
    void 관리자와_자기_자신은_정지할_수_없다() throws Exception {
        long admin = newAdmin("운영자");
        long other = newAdmin("다른운영자");
        adminPost(admin, "/api/admin/users/{id}/suspend", "{\"reason\":\"실수\"}", admin).andExpect(status().isBadRequest());
        adminPost(admin, "/api/admin/users/{id}/suspend", "{\"reason\":\"실수\"}", other).andExpect(status().isBadRequest());
        assertThat(q("SELECT count(*) FROM app_user WHERE suspended_at IS NOT NULL AND id IN (?, ?)", admin, other)).isEqualTo("0");
    }

    @Test
    void 글_일괄_숨김은_리뷰와_사진을_숨기고_집계에서_뺀다() throws Exception {
        long admin = newAdmin("운영자");
        long user = newUser("도배유저");
        long a = newBakery("도배 빵집A", "ACTIVE");
        long b = newBakery("도배 빵집B", "ACTIVE");
        long reviewA = review(user, a, 5);
        review(user, b, 5);
        photo(reviewA, 1);
        mvc.perform(get("/api/bakeries/{id}", a)).andExpect(jsonPath("$.reviewCount").value(1));

        adminPost(admin, "/api/admin/users/{id}/hide-content", "{}", user).andExpect(status().isBadRequest());
        adminPost(admin, "/api/admin/users/{id}/hide-content", "{\"reason\":\"광고 도배\"}", user).andExpect(status().isOk());
        assertThat(q("SELECT count(*) FROM review WHERE user_id = ? AND status = 'HIDDEN' AND hidden_reason = 'ADMIN'", user)).isEqualTo("2");
        assertThat(q("SELECT count(*) FROM review_photo WHERE status = 'HIDDEN'")).isNotEqualTo("0");
        mvc.perform(get("/api/bakeries/{id}", a)).andExpect(jsonPath("$.reviewCount").value(0));
        mvc.perform(get("/api/bakeries/{id}/reviews", b)).andExpect(jsonPath("$.items", hasSize(0)));
        adminGet(admin, "/api/admin/audit-logs?targetType=USER&targetId=" + user)
                .andExpect(jsonPath("$.items[0].detail").value(org.hamcrest.Matchers.containsString("리뷰 2개")));
    }
}
