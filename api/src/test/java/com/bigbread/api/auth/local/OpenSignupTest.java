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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 메일 없이 아이디·비밀번호만으로 바로 가입하는 모드(AUTH_SIGNUP_MODE=open). */
@SpringBootTest(properties = {"bigbread.auth.signup-mode=open", "bigbread.kakao.rest-api-key="})
@AutoConfigureMockMvc
@Transactional
class OpenSignupTest {

    private static final String PASSWORD = "BreadLover7!x";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuthRateLimits limits;

    private Cookie xsrf;

    @BeforeEach
    void setUp() throws Exception {
        limits.reset();
        xsrf = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    void 가입은_열려_있고_이메일은_필요_없고_메일_복구는_없다() throws Exception {
        mvc.perform(get("/api/auth/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.signupEnabled").value(true))
                .andExpect(jsonPath("$.collectEmail").value(false))
                .andExpect(jsonPath("$.verifyEmail").value(false))
                .andExpect(jsonPath("$.recoveryEnabled").value(false));
    }

    @Test
    void 아이디와_비밀번호만으로_가입하면_바로_로그인된_상태가_된다() throws Exception {
        CookieSession browser = new CookieSession(xsrf);
        signup(browser, "openuser1", "빵순이", true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.nickname").value("빵순이"));

        // 응답에서 받은 세션으로 내 정보가 조회된다
        browser.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(true));
        // 이메일은 어디에도 저장되지 않는다
        assertThat(jdbc.queryForObject("SELECT email FROM local_credential WHERE login_id = 'openuser1'", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pending_signup", Long.class)).isZero();

        // 같은 아이디·비밀번호로 다시 로그인할 수 있다
        CookieSession other = new CookieSession(xsrf);
        other.perform(mvc, other.withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"openuser1\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 이메일이_없는_계정이_여러_개여도_가입된다() throws Exception {
        signup(new CookieSession(xsrf), "openuser2", "첫째손님", true).andExpect(status().isCreated());
        signup(new CookieSession(xsrf), "openuser3", "둘째손님", true).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM local_credential WHERE email IS NULL AND login_id LIKE 'openuser%'",
                Long.class)).isEqualTo(2);
    }

    @Test
    void 이미_쓰는_아이디와_동의_없음과_약한_비밀번호는_거부한다() throws Exception {
        signup(new CookieSession(xsrf), "openuser4", "첫째손님", true).andExpect(status().isCreated());
        signup(new CookieSession(xsrf), "OpenUser4", "둘째손님", true).andExpect(status().isConflict());
        signup(new CookieSession(xsrf), "openuser5", "셋째손님", false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("동의")));
        mvc.perform(withCsrf(post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"openuser6\",\"password\":\"short1!\",\"nickname\":\"넷째손님\",\"agreedToTerms\":true}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM local_credential WHERE login_id = 'openuser5'", Long.class)).isZero();
    }

    @Test
    void 아이디_찾기와_비밀번호_재설정은_쓸_수_없다() throws Exception {
        mvc.perform(withCsrf(post("/api/auth/password/forgot")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"openuser1\"}")).andExpect(status().isServiceUnavailable());
        mvc.perform(withCsrf(post("/api/auth/find-id")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.com\"}")).andExpect(status().isServiceUnavailable());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCsrf(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }

    private ResultActions signup(CookieSession browser, String loginId, String nickname, boolean agreed) throws Exception {
        return browser.perform(mvc, browser.withCsrf(post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"loginId\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\",\"agreedToTerms\":%s}",
                        loginId, PASSWORD, nickname, agreed)));
    }
}
