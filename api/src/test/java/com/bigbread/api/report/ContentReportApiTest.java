package com.bigbread.api.report;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bigbread.api.user.UserApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.assertThat;

class ContentReportApiTest extends UserApiTestSupport {

    private long review(long user, long bakery) throws Exception {
        mvc.perform(withCsrf(put("/api/my/reviews/{id}", bakery)).with(as(user))
                .contentType(APPLICATION_JSON).content("{\"rating\":4,\"content\":\"리뷰\"}")).andExpect(status().isOk());
        return jdbc.queryForObject("SELECT id FROM review WHERE bakery_id = ? AND user_id = ?", Long.class, bakery, user);
    }

    private ResultActions report(long reporter, String type, long target, String reason, String detail) throws Exception {
        String body = "{\"targetType\":\"" + type + "\",\"targetId\":" + target + ",\"reason\":\"" + reason + "\""
                + (detail == null ? "" : ",\"detail\":\"" + detail + "\"") + "}";
        return mvc.perform(withCsrf(post("/api/my/reports")).with(as(reporter)).contentType(APPLICATION_JSON).content(body));
    }

    private String reviewStatus(long reviewId) {
        return jdbc.queryForObject("SELECT status FROM review WHERE id = ?", String.class, reviewId);
    }

    @Test
    void 비로그인은_신고할_수_없다() throws Exception {
        mvc.perform(withCsrf(post("/api/my/reports")).contentType(APPLICATION_JSON)
                .content("{\"targetType\":\"REVIEW\",\"targetId\":1,\"reason\":\"SPAM\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void 신고는_한_번만_되고_내_글과_없는_대상은_신고할_수_없다() throws Exception {
        long author = newUser("작성자");
        long other = newUser("신고자");
        long bakery = newBakery("신고A", "ACTIVE");
        long review = review(author, bakery);

        report(other, "REVIEW", review, "SPAM", "광고예요").andExpect(status().isCreated());
        report(other, "REVIEW", review, "SPAM", null).andExpect(status().isConflict());
        report(author, "REVIEW", review, "SPAM", null).andExpect(status().isBadRequest());
        report(other, "REVIEW", 999_999_999L, "SPAM", null).andExpect(status().isNotFound());
        report(other, "PHOTO", 999_999_999L, "SPAM", null).andExpect(status().isNotFound());
        report(newUser("또다른"), "REVIEW", review, "OTHER", null).andExpect(status().isBadRequest());
        assertThat(reviewStatus(review)).isEqualTo("VISIBLE");
    }

    @Test
    void 서로_다른_세_명이_신고하면_자동으로_숨겨지고_평점_집계에서_빠진다() throws Exception {
        long author = newUser("작성자");
        long bakery = newBakery("신고A", "ACTIVE");
        long review = review(author, bakery);
        mvc.perform(get("/api/bakeries/{id}", bakery)).andExpect(jsonPath("$.reviewCount").value(1));

        report(newUser("신고1"), "REVIEW", review, "ABUSE", null).andExpect(status().isCreated());
        report(newUser("신고2"), "REVIEW", review, "ABUSE", null).andExpect(status().isCreated());
        assertThat(reviewStatus(review)).isEqualTo("VISIBLE");
        mvc.perform(get("/api/bakeries/{id}/reviews", bakery)).andExpect(jsonPath("$.items", hasSize(1)));

        report(newUser("신고3"), "REVIEW", review, "ABUSE", null).andExpect(status().isCreated());
        assertThat(reviewStatus(review)).isEqualTo("HIDDEN");
        assertThat(jdbc.queryForObject("SELECT hidden_reason FROM review WHERE id = ?", String.class, review)).isEqualTo("REPORTS");
        mvc.perform(get("/api/bakeries/{id}/reviews", bakery)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/bakeries/{id}", bakery)).andExpect(jsonPath("$.reviewCount").value(0));

        // 작성자는 숨겨진 사실을 알 수 있다
        mvc.perform(get("/api/my/reviews/{id}", bakery).with(as(author))).andExpect(jsonPath("$.hidden").value(true));
        // 숨겨진 뒤에는 더 신고할 수 없다 (이미 보이지 않는 글)
        report(newUser("신고4"), "REVIEW", review, "ABUSE", null).andExpect(status().isNotFound());
    }

    @Test
    void 사진도_세_명이_신고하면_숨겨진다() throws Exception {
        long author = newUser("작성자");
        long bakery = newBakery("신고A", "ACTIVE");
        long review = review(author, bakery);
        long photo = jdbc.queryForObject("""
                INSERT INTO review_photo (review_id, storage_key, width, height, byte_size, seq)
                VALUES (?, ?, 100, 100, 10, 1) RETURNING id
                """, Long.class, review, "a".repeat(32));

        report(newUser("신고1"), "PHOTO", photo, "PRIVACY", null).andExpect(status().isCreated());
        report(newUser("신고2"), "PHOTO", photo, "PRIVACY", null).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT status FROM review_photo WHERE id = ?", String.class, photo)).isEqualTo("VISIBLE");
        report(newUser("신고3"), "PHOTO", photo, "PRIVACY", null).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT status FROM review_photo WHERE id = ?", String.class, photo)).isEqualTo("HIDDEN");
        // 리뷰 글은 그대로 보인다
        assertThat(reviewStatus(review)).isEqualTo("VISIBLE");
    }

    @Test
    void 이미_처리된_신고는_숨김_기준에_세지_않는다() throws Exception {
        long author = newUser("작성자");
        long bakery = newBakery("신고A", "ACTIVE");
        long review = review(author, bakery);
        report(newUser("신고1"), "REVIEW", review, "SPAM", null).andExpect(status().isCreated());
        report(newUser("신고2"), "REVIEW", review, "SPAM", null).andExpect(status().isCreated());
        jdbc.update("UPDATE content_report SET status = 'DISMISSED' WHERE target_id = ?", review);
        report(newUser("신고3"), "REVIEW", review, "SPAM", null).andExpect(status().isCreated());
        assertThat(reviewStatus(review)).isEqualTo("VISIBLE");
    }

    @Test
    void 작성자가_지우면_처리_전_신고는_처리됨으로_닫힌다() throws Exception {
        long author = newUser("작성자");
        long bakery = newBakery("신고A", "ACTIVE");
        long review = review(author, bakery);
        report(newUser("신고1"), "REVIEW", review, "SPAM", null).andExpect(status().isCreated());

        mvc.perform(withCsrf(delete("/api/my/reviews/{id}", bakery)).with(as(author))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM content_report WHERE target_id = ?", String.class, review))
                .isEqualTo("ACTIONED");
    }

    @Test
    void 하루_신고_한도를_넘으면_429다() throws Exception {
        long reporter = newUser("신고왕");
        long author = newUser("작성자");
        long bakery = newBakery("신고A", "ACTIVE");
        long review = review(author, bakery);
        for (int i = 0; i < ContentReportService.MAX_PER_DAY; i++) {
            jdbc.update("INSERT INTO content_report (reporter_id, target_type, target_id, reason) VALUES (?, 'REVIEW', ?, 'SPAM')",
                    reporter, 2_000_000L + i);
        }
        report(reporter, "REVIEW", review, "SPAM", null).andExpect(status().isTooManyRequests());
    }
}
