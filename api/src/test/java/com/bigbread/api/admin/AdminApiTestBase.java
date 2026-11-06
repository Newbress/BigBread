package com.bigbread.api.admin;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.bigbread.api.user.UserApiTestSupport;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/** 관리자 API 테스트의 공통 도구. 카카오 장소 검색은 가짜로 바꿔 둔다. */
abstract class AdminApiTestBase extends UserApiTestSupport {

    @MockitoBean
    protected PlaceLookup lookup;

    protected ResultActions adminGet(long admin, String url, Object... vars) throws Exception {
        return mvc.perform(get(url, vars).with(as(admin)));
    }

    protected ResultActions adminPost(long admin, String url, String json, Object... vars) throws Exception {
        return mvc.perform(withCsrf(post(url, vars)).with(as(admin)).contentType(APPLICATION_JSON).content(json));
    }

    protected ResultActions adminPatch(long admin, String url, String json, Object... vars) throws Exception {
        return mvc.perform(withCsrf(patch(url, vars)).with(as(admin)).contentType(APPLICATION_JSON).content(json));
    }

    protected long review(long user, long bakery, int rating) throws Exception {
        mvc.perform(withCsrf(put("/api/my/reviews/{id}", bakery)).with(as(user)).contentType(APPLICATION_JSON)
                .content("{\"rating\":" + rating + ",\"content\":\"리뷰 내용\"}"));
        return jdbc.queryForObject("SELECT id FROM review WHERE bakery_id = ? AND user_id = ?", Long.class, bakery, user);
    }

    protected void report(long reporter, String type, long target) throws Exception {
        mvc.perform(withCsrf(post("/api/my/reports")).with(as(reporter)).contentType(APPLICATION_JSON)
                .content("{\"targetType\":\"" + type + "\",\"targetId\":" + target + ",\"reason\":\"SPAM\",\"detail\":\"광고예요\"}"));
    }

    protected long photo(long review, int seq) {
        return jdbc.queryForObject("""
                INSERT INTO review_photo (review_id, storage_key, width, height, byte_size, seq)
                VALUES (?, ?, 100, 100, 10, ?) RETURNING id
                """, Long.class, review, String.format("%032x", System.nanoTime() + seq), seq);
    }

    protected String q(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }
}
