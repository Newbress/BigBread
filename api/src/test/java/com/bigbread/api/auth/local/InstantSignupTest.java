package com.bigbread.api.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bigbread.api.auth.CookieSession;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 이메일은 받되 인증 없이 바로 가입하는 모드(AUTH_SIGNUP_MODE=instant). 이메일은 복구 메일에만 쓴다. */
@SpringBootTest(properties = {"bigbread.auth.signup-mode=instant", "bigbread.auth.base-url=http://localhost:5173",
        "bigbread.kakao.rest-api-key="})
@AutoConfigureMockMvc
@Transactional
@Import(InstantSignupTest.MailConfig.class)
class InstantSignupTest {

    @TestConfiguration
    static class MailConfig {
        @Bean
        @Primary
        CapturingAuthMailer capturingAuthMailer() {
            return new CapturingAuthMailer();
        }
    }

    private static final String PASSWORD = "BreadLover7!x";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuthRateLimits limits;

    @Autowired
    CapturingAuthMailer mailer;

    private Cookie xsrf;

    @BeforeEach
    void setUp() throws Exception {
        mailer.clear();
        limits.reset();
        xsrf = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    void 이메일은_받지만_인증은_없고_메일_복구는_쓸_수_있다() throws Exception {
        mvc.perform(get("/api/auth/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.signupEnabled").value(true))
                .andExpect(jsonPath("$.collectEmail").value(true))
                .andExpect(jsonPath("$.verifyEmail").value(false))
                .andExpect(jsonPath("$.recoveryEnabled").value(true));
    }

    @Test
    void 가입하면_인증_메일_없이_바로_로그인되고_이메일은_소문자로_저장된다() throws Exception {
        CookieSession browser = new CookieSession(xsrf);
        signup(browser, "instant01", "Me@Example.com").andExpect(status().isCreated())
                .andExpect(jsonPath("$.authenticated").value(true));

        assertThat(mailer.verifications).isEmpty();
        assertThat(jdbc.queryForObject("SELECT email FROM local_credential WHERE login_id = 'instant01'", String.class))
                .isEqualTo("me@example.com");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pending_signup", Long.class)).isZero();
        browser.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void 이메일이_없거나_이미_쓰는_이메일_아이디는_거부한다() throws Exception {
        signup(new CookieSession(xsrf), "instant02", "dup@example.com").andExpect(status().isCreated());
        signup(new CookieSession(xsrf), "instant03", "DUP@example.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("이미 가입된 이메일")));
        signup(new CookieSession(xsrf), "instant02", "other@example.com").andExpect(status().isConflict());
        signup(new CookieSession(xsrf), "instant04", "").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM local_credential WHERE login_id LIKE 'instant%'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void 가입한_이메일로_비밀번호_재설정_메일이_간다() throws Exception {
        signup(new CookieSession(xsrf), "instant05", "reset@example.com").andExpect(status().isCreated());
        mvc.perform(post("/api/auth/password/forgot").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"instant05\"}"))
                .andExpect(status().isAccepted());
        assertThat(mailer.passwordResets).hasSize(1);
        assertThat(mailer.passwordResets.get(0).email()).isEqualTo("reset@example.com");
    }

    private ResultActions signup(CookieSession browser, String loginId, String email) throws Exception {
        return browser.perform(mvc, browser.withCsrf(post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"loginId\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"즉시손님\",\"agreedToTerms\":true}",
                        loginId, email, PASSWORD)));
    }
}
