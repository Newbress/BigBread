package com.bigbread.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class AdminContentReportApiTest extends AdminApiTestBase {

    private String resolveBody(String type, long id, String action) {
        return "{\"targetType\":\"" + type + "\",\"targetId\":" + id + ",\"action\":\"" + action + "\"}";
    }

    private String reviewStatus(long id) {
        return q("SELECT status FROM review WHERE id = ?", id);
    }

    @Test
    void 신고는_대상별로_묶여서_미리보기와_함께_보인다() throws Exception {
        long admin = newAdmin("운영자");
        long author = newUser("작성자");
        long bakery = newBakery("신고 빵집", "ACTIVE");
        long review = review(author, bakery, 3);
        photo(review, 1);
        report(newUser("신고1"), "REVIEW", review);
        report(newUser("신고2"), "REVIEW", review);

        adminGet(admin, "/api/admin/content-reports?status=PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.targetId==" + review + " && @.targetType=='REVIEW')].pendingCount").value(2))
                .andExpect(jsonPath("$.items[?(@.targetId==" + review + " && @.targetType=='REVIEW')].target.authorNickname").value("작성자"))
                .andExpect(jsonPath("$.items[?(@.targetId==" + review + " && @.targetType=='REVIEW')].target.bakeryName").value("신고 빵집"))
                .andExpect(jsonPath("$.items[?(@.targetId==" + review + " && @.targetType=='REVIEW')].target.photos[0].thumbUrl")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.startsWith("/api/admin/photos/"))));
    }

    @Test
    void 문제없음으로_처리하면_신고가_닫히고_자동_숨김도_풀린다() throws Exception {
        long admin = newAdmin("운영자");
        long bakery = newBakery("신고 빵집", "ACTIVE");
        long review = review(newUser("작성자"), bakery, 5);
        for (int i = 0; i < 3; i++) {
            report(newUser("신고" + i), "REVIEW", review);
        }
        assertThat(reviewStatus(review)).isEqualTo("HIDDEN");
        mvc.perform(get("/api/bakeries/{id}", bakery)).andExpect(jsonPath("$.reviewCount").value(0));

        adminPost(admin, "/api/admin/content-reports/resolve", resolveBody("REVIEW", review, "DISMISS")).andExpect(status().isNoContent());
        assertThat(reviewStatus(review)).isEqualTo("VISIBLE");
        assertThat(q("SELECT hidden_reason FROM review WHERE id = ?", review)).isNull();
        assertThat(q("SELECT count(*) FROM content_report WHERE target_id = ? AND status = 'DISMISSED'", review)).isEqualTo("3");
        mvc.perform(get("/api/bakeries/{id}", bakery)).andExpect(jsonPath("$.reviewCount").value(1));
        adminGet(admin, "/api/admin/content-reports?status=PENDING")
                .andExpect(jsonPath("$.items[?(@.targetId==" + review + ")]", hasSize(0)));
        adminGet(admin, "/api/admin/content-reports?status=HANDLED")
                .andExpect(jsonPath("$.items[?(@.targetId==" + review + ")]", hasSize(1)));
    }

    @Test
    void 운영자가_숨긴_것은_문제없음으로_닫아도_숨김이_유지되고_복구로_풀_수_있다() throws Exception {
        long admin = newAdmin("운영자");
        long bakery = newBakery("신고 빵집", "ACTIVE");
        long review = review(newUser("작성자"), bakery, 5);
        report(newUser("신고1"), "REVIEW", review);

        adminPost(admin, "/api/admin/content-reports/resolve", resolveBody("REVIEW", review, "HIDE")).andExpect(status().isNoContent());
        assertThat(reviewStatus(review)).isEqualTo("HIDDEN");
        assertThat(q("SELECT hidden_reason FROM review WHERE id = ?", review)).isEqualTo("ADMIN");

        // 새 신고가 들어와 문제없음으로 닫아도, 운영자가 직접 숨긴 건 그대로다
        report(newUser("신고2"), "REVIEW", review);
        adminPost(admin, "/api/admin/content-reports/resolve", resolveBody("REVIEW", review, "DISMISS")).andExpect(status().isNoContent());
        assertThat(reviewStatus(review)).isEqualTo("HIDDEN");

        adminPost(admin, "/api/admin/content-reports/resolve", resolveBody("REVIEW", review, "RESTORE")).andExpect(status().isNoContent());
        assertThat(reviewStatus(review)).isEqualTo("VISIBLE");
    }

    @Test
    void 삭제하면_리뷰가_사라지고_평점_집계에서_빠진다() throws Exception {
        long admin = newAdmin("운영자");
        long bakery = newBakery("신고 빵집", "ACTIVE");
        long review = review(newUser("작성자"), bakery, 5);
        report(newUser("신고1"), "REVIEW", review);

        adminPost(admin, "/api/admin/content-reports/resolve", resolveBody("REVIEW", review, "DELETE")).andExpect(status().isNoContent());
        assertThat(q("SELECT count(*) FROM review WHERE id = ?", review)).isEqualTo("0");
        mvc.perform(get("/api/bakeries/{id}", bakery)).andExpect(jsonPath("$.reviewCount").value(0));
        assertThat(q("SELECT status FROM content_report WHERE target_id = ?", review)).isEqualTo("ACTIONED");
    }

    @Test
    void 사진_신고도_숨김_복구_삭제를_할_수_있다() throws Exception {
        long admin = newAdmin("운영자");
        long bakery = newBakery("신고 빵집", "ACTIVE");
        long review = review(newUser("작성자"), bakery, 5);
        long photo = photo(review, 1);
        for (int i = 0; i < 3; i++) {
            report(newUser("신고" + i), "PHOTO", photo);
        }
        assertThat(q("SELECT status FROM review_photo WHERE id = ?", photo)).isEqualTo("HIDDEN");

        adminPost(admin, "/api/admin/content-reports/resolve", resolveBody("PHOTO", photo, "DISMISS")).andExpect(status().isNoContent());
        assertThat(q("SELECT status FROM review_photo WHERE id = ?", photo)).isEqualTo("VISIBLE");

        adminPost(admin, "/api/admin/content-reports/resolve", resolveBody("PHOTO", photo, "HIDE")).andExpect(status().isNoContent());
        assertThat(q("SELECT status FROM review_photo WHERE id = ?", photo)).isEqualTo("HIDDEN");

        adminPost(admin, "/api/admin/content-reports/resolve", resolveBody("PHOTO", photo, "DELETE")).andExpect(status().isNoContent());
        assertThat(q("SELECT count(*) FROM review_photo WHERE id = ?", photo)).isEqualTo("0");
        assertThat(q("SELECT status FROM review WHERE id = ?", review)).isEqualTo("VISIBLE");
    }

    @Test
    void 이미_사라진_대상의_신고는_닫기만_한다() throws Exception {
        long admin = newAdmin("운영자");
        jdbc.update("INSERT INTO content_report (reporter_id, target_type, target_id, reason) VALUES (?, 'REVIEW', 777777, 'SPAM')", newUser("신고1"));
        adminPost(admin, "/api/admin/content-reports/resolve", resolveBody("REVIEW", 777777, "DELETE")).andExpect(status().isNoContent());
        assertThat(q("SELECT status FROM content_report WHERE target_id = 777777")).isEqualTo("ACTIONED");
    }

    @Test
    void 잘못된_요청은_400이고_처리는_기록된다() throws Exception {
        long admin = newAdmin("운영자");
        adminPost(admin, "/api/admin/content-reports/resolve", "{\"targetType\":\"REVIEW\",\"targetId\":1}").andExpect(status().isBadRequest());
        adminPost(admin, "/api/admin/content-reports/resolve", "{\"targetType\":\"REVIEW\",\"targetId\":1,\"action\":\"EXPLODE\"}").andExpect(status().isBadRequest());

        long bakery = newBakery("신고 빵집", "ACTIVE");
        long review = review(newUser("작성자"), bakery, 5);
        adminPost(admin, "/api/admin/content-reports/resolve", resolveBody("REVIEW", review, "HIDE")).andExpect(status().isNoContent());
        adminGet(admin, "/api/admin/audit-logs?targetType=REVIEW&targetId=" + review)
                .andExpect(jsonPath("$.items[0].action").value("CONTENT_REPORT_HIDE"));
    }
}
