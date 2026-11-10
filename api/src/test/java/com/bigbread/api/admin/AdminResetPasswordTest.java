package com.bigbread.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bigbread.api.auth.local.LocalAuthRules;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 비밀번호를 잊은 이용자를 위한 임시 비밀번호 발급(관리자). */
class AdminResetPasswordTest extends AdminApiTestBase {

    @Autowired
    PasswordEncoder encoder;

    private long localUser(String loginId) {
        long user = newUser("로컬" + loginId);
        jdbc.update("DELETE FROM oauth_account WHERE user_id = ?", user);
        jdbc.update("INSERT INTO local_credential (user_id, login_id, email, password_hash, terms_agreed_at, failed_attempts, locked_until) "
                + "VALUES (?, ?, NULL, ?, now(), 3, now() + interval '10 minutes')", user, loginId, encoder.encode("Old-Password-1!"));
        return user;
    }

    @Test
    void 임시_비밀번호를_발급하면_규칙에_맞고_기록이_남고_잠금이_풀린다() throws Exception {
        long admin = newAdmin("운영자");
        long user = localUser("forgetful1");

        String body = adminPost(admin, "/api/admin/users/{id}/reset-password", "{}", user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("forgetful1"))
                .andReturn().getResponse().getContentAsString();
        String temp = com.jayway.jsonpath.JsonPath.read(body, "$.temporaryPassword");

        assertThat(LocalAuthRules.passwordError(temp, "forgetful1")).isNull();
        String hash = q("SELECT password_hash FROM local_credential WHERE user_id = ?", user);
        assertThat(encoder.matches(temp, hash)).isTrue();
        assertThat(encoder.matches("Old-Password-1!", hash)).isFalse();
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM local_credential WHERE user_id = ?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT locked_until FROM local_credential WHERE user_id = ?", Object.class, user)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit_log WHERE action = 'USER_RESET_PASSWORD' AND target_id = ?",
                Long.class, user)).isEqualTo(1);
        // 로그에는 비밀번호 자체가 남지 않는다
        assertThat(q("SELECT detail FROM admin_audit_log WHERE action = 'USER_RESET_PASSWORD' AND target_id = ?", user))
                .doesNotContain(temp);
    }

    @Test
    void 소셜_계정과_관리자_계정과_일반_사용자는_대상이_아니다() throws Exception {
        long admin = newAdmin("운영자");
        long social = newUser("소셜전용");
        adminPost(admin, "/api/admin/users/{id}/reset-password", "{}", social).andExpect(status().isBadRequest());

        long otherAdmin = localUser("adminlocal1");
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE id = ?", otherAdmin);
        adminPost(admin, "/api/admin/users/{id}/reset-password", "{}", otherAdmin).andExpect(status().isBadRequest());

        long normal = localUser("normaluser1");
        mvc.perform(withCsrf(post("/api/admin/users/{id}/reset-password", admin)).with(as(normal)).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        adminPost(admin, "/api/admin/users/{id}/reset-password", "{}", 999_999_999L).andExpect(status().isNotFound());
    }

    @Test
    void 임시_비밀번호는_매번_규칙을_지킨다() {
        for (int i = 0; i < 200; i++) {
            String p = AdminUserService.newTemporaryPassword("tempuser01");
            assertThat(p).hasSize(12);
            assertThat(LocalAuthRules.passwordError(p, "tempuser01")).isNull();
        }
    }
}
