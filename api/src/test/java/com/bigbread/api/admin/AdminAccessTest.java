package com.bigbread.api.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class AdminAccessTest extends AdminApiTestBase {

    @Test
    void 비로그인은_401이다() throws Exception {
        mvc.perform(get("/api/admin/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void 일반_사용자는_403이다() throws Exception {
        long user = newUser("일반");
        mvc.perform(get("/api/admin/summary").with(as(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bakery-reports").with(as(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/content-reports").with(as(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/bakeries").with(as(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(as(user))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit-logs").with(as(user))).andExpect(status().isForbidden());
    }

    @Test
    void 역할은_세션이_아니라_DB가_기준이다() throws Exception {
        long admin = newAdmin("운영자");
        // 세션에는 USER로 들어 있어도 DB에서 관리자면 바로 쓸 수 있다 (다시 로그인할 필요 없음)
        mvc.perform(get("/api/admin/summary").with(as(admin))).andExpect(status().isOk());
        // 세션에 ADMIN이라고 들어 있어도 DB에서 아니면 막힌다 (권한을 거둔 경우)
        long demoted = newUser("강등됨");
        mvc.perform(get("/api/admin/summary").with(asRole(demoted, "ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void 정지된_관리자도_막힌다() throws Exception {
        long admin = newAdmin("운영자");
        jdbc.update("UPDATE app_user SET suspended_at = now() WHERE id = ?", admin);
        mvc.perform(get("/api/admin/summary").with(as(admin))).andExpect(status().isForbidden());
    }

    @Test
    void 요약에는_처리할_일의_수가_나온다() throws Exception {
        long admin = newAdmin("운영자");
        long author = newUser("작성자");
        long bakery = newBakery("요약A", "ACTIVE");
        long review = review(author, bakery, 4);
        report(newUser("신고1"), "REVIEW", review);
        jdbc.update("INSERT INTO bakery_report (user_id, kind, name, address) VALUES (?, 'NEW', '가게', '대전')", author);

        long before = jdbc.queryForObject("SELECT count(*) FROM bakery_report WHERE status = 'PENDING'", Long.class);
        adminGet(admin, "/api/admin/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingBakeryReports").value(before))
                .andExpect(jsonPath("$.pendingContentReports").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        adminGet(admin, "/api/admin/audit-logs").andExpect(jsonPath("$.items", hasSize(0)));
    }
}
