package com.bigbread.api.review;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bigbread.api.user.UserApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class ReviewApiTest extends UserApiTestSupport {

    private ResultActions write(long user, long bakery, String json) throws Exception {
        return mvc.perform(withCsrf(put("/api/my/reviews/{id}", bakery)).with(as(user))
                .contentType(APPLICATION_JSON).content(json));
    }

    @Test
    void 비로그인은_쓰기_수정_삭제가_401이고_조회는_된다() throws Exception {
        long bakery = newBakery("리뷰A", "ACTIVE");
        mvc.perform(withCsrf(put("/api/my/reviews/{id}", bakery)).contentType(APPLICATION_JSON)
                .content("{\"rating\":5}")).andExpect(status().isUnauthorized());
        mvc.perform(withCsrf(delete("/api/my/reviews/{id}", bakery))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/my/reviews")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bakeries/{id}/reviews", bakery)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void 리뷰를_쓰면_목록에_최신순으로_나오고_내_것만_mine이다() throws Exception {
        long me = newUser("나");
        long other = newUser("다른사람");
        long bakery = newBakery("리뷰A", "ACTIVE");

        write(other, bakery, "{\"rating\":4,\"content\":\"먼저 쓴 리뷰\"}").andExpect(status().isOk());
        write(me, bakery, "{\"rating\":5,\"content\":\"  소금빵이 맛있어요  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.content").value("소금빵이 맛있어요"))
                .andExpect(jsonPath("$.mine").value(true));

        mvc.perform(get("/api/bakeries/{id}/reviews", bakery).with(as(me)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].nickname", contains("나", "다른사람")))
                .andExpect(jsonPath("$.items[*].mine", contains(true, false)))
                .andExpect(jsonPath("$.items[0].userId").doesNotExist());
        // 비로그인에게는 mine이 모두 false
        mvc.perform(get("/api/bakeries/{id}/reviews", bakery))
                .andExpect(jsonPath("$.items[*].mine", contains(false, false)));
    }

    @Test
    void 같은_빵집에_다시_쓰면_수정되고_하나만_남는다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("리뷰A", "ACTIVE");

        write(me, bakery, "{\"rating\":2,\"content\":\"별로\"}").andExpect(status().isOk());
        write(me, bakery, "{\"rating\":4}").andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.content").doesNotExist());

        mvc.perform(get("/api/bakeries/{id}/reviews", bakery)).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/my/reviews/{id}", bakery).with(as(me))).andExpect(jsonPath("$.rating").value(4));
    }

    @Test
    void 잘못된_평점과_너무_긴_리뷰는_400이다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("리뷰A", "ACTIVE");
        write(me, bakery, "{\"rating\":0}").andExpect(status().isBadRequest());
        write(me, bakery, "{\"rating\":6}").andExpect(status().isBadRequest());
        write(me, bakery, "{\"rating\":3,\"content\":\"" + "가".repeat(1001) + "\"}").andExpect(status().isBadRequest());
        write(me, bakery, "{\"rating\":3,\"content\":\"" + "가".repeat(1000) + "\"}").andExpect(status().isOk());
    }

    @Test
    void 없거나_숨김인_빵집에는_쓸_수_없다() throws Exception {
        long me = newUser("나");
        long hidden = newBakery("숨김", "HIDDEN");
        write(me, hidden, "{\"rating\":5}").andExpect(status().isNotFound());
        write(me, 999_999_999L, "{\"rating\":5}").andExpect(status().isNotFound());
        mvc.perform(get("/api/bakeries/{id}/reviews", hidden)).andExpect(status().isNotFound());
    }

    @Test
    void 삭제는_멱등이고_내_리뷰만_지운다() throws Exception {
        long me = newUser("나");
        long other = newUser("다른사람");
        long bakery = newBakery("리뷰A", "ACTIVE");
        write(me, bakery, "{\"rating\":5}").andExpect(status().isOk());
        write(other, bakery, "{\"rating\":1}").andExpect(status().isOk());

        mvc.perform(withCsrf(delete("/api/my/reviews/{id}", bakery)).with(as(me))).andExpect(status().isNoContent());
        mvc.perform(withCsrf(delete("/api/my/reviews/{id}", bakery)).with(as(me))).andExpect(status().isNoContent());
        mvc.perform(get("/api/my/reviews/{id}", bakery).with(as(me))).andExpect(status().isNotFound());
        mvc.perform(get("/api/bakeries/{id}/reviews", bakery)).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void 숨김_리뷰는_목록과_집계에서_빠지지만_본인은_볼_수_있다() throws Exception {
        long me = newUser("나");
        long bakery = newBakery("리뷰A", "ACTIVE");
        write(me, bakery, "{\"rating\":5}").andExpect(status().isOk());
        jdbc.update("UPDATE review SET status = 'HIDDEN' WHERE bakery_id = ?", bakery);
        jdbc.queryForList("SELECT recalc_bakery_stats(?)", bakery);

        mvc.perform(get("/api/bakeries/{id}/reviews", bakery)).andExpect(jsonPath("$.items", empty()));
        mvc.perform(get("/api/bakeries/{id}", bakery)).andExpect(jsonPath("$.reviewCount").value(0));
        mvc.perform(get("/api/my/reviews/{id}", bakery).with(as(me))).andExpect(status().isOk());
    }

    @Test
    void 내_리뷰_목록은_최근에_쓴_순서이고_숨김_빵집은_빠진다() throws Exception {
        long me = newUser("나");
        long a = newBakery("리뷰A", "ACTIVE");
        long b = newBakery("리뷰B", "ACTIVE");
        write(me, a, "{\"rating\":3}").andExpect(status().isOk());
        write(me, b, "{\"rating\":4}").andExpect(status().isOk());
        mvc.perform(get("/api/my/reviews").with(as(me)))
                .andExpect(jsonPath("$[*].bakery.name", contains("리뷰B", "리뷰A")));
        jdbc.update("UPDATE bakery SET status = 'HIDDEN' WHERE id = ?", b);
        mvc.perform(get("/api/my/reviews").with(as(me))).andExpect(jsonPath("$[*].bakery.name", contains("리뷰A")));
    }
}
