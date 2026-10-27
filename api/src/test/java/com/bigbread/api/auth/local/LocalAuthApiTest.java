package com.bigbread.api.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.bigbread.api.auth.CookieSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** 가입 → 이메일 인증 → 로그인 → 로그아웃 전체 흐름. 프론트와 같이 CSRF 쿠키를 받아 헤더로 돌려보낸다. */
@SpringBootTest(properties = {"bigbread.auth.base-url=http://localhost:5173", "bigbread.kakao.rest-api-key="})
@AutoConfigureMockMvc
@Transactional
@Import(LocalAuthApiTest.MailConfig.class)
class LocalAuthApiTest {

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
    CapturingAuthMailer mailer;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuthRateLimits limits;

    private Cookie xsrf;

    @BeforeEach
    void setUp() throws Exception {
        mailer.clear();
        limits.reset(); // 한 클래스에서 같은 IP로 가입을 많이 하므로 요청 제한 기록을 비운다
        xsrf = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    void 메일을_보낼_수_있으면_가입_기능이_켜진_것으로_알려준다() throws Exception {
        mvc.perform(get("/api/auth/options")).andExpect(status().isOk()).andExpect(jsonPath("$.signupEnabled").value(true));
    }

    @Test
    void 가입_인증_로그인_로그아웃_전체_흐름() throws Exception {
        signup("BreadFan", "Fan@Example.com", PASSWORD, "빵순이").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(containsString("인증 메일")));
        assertThat(mailer.verifications).hasSize(1);
        CapturingAuthMailer.Verification mail = mailer.last();
        assertThat(mail.email()).isEqualTo("fan@example.com"); // 소문자로 정규화
        assertThat(mail.link()).startsWith("http://localhost:5173/verify-email?token=");
        assertThat(mail.validFor().toHours()).isEqualTo(24);

        // 인증 전에는 로그인할 수 없다.
        login("breadfan", PASSWORD).andExpect(status().isUnauthorized());

        verify(mail.token()).andExpect(status().isNoContent());

        // 아이디는 대소문자를 구분하지 않는다.
        CookieSession browser = new CookieSession(xsrf);
        browser.perform(mvc, get("/api/me")); // 로그인 전에 이미 받은 세션이 있어도
        String before = browser.value("SESSION");
        login("BREADFAN", PASSWORD, browser)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.nickname").value("빵순이"))
                .andExpect(jsonPath("$.user.email").value("fan@example.com"));
        assertThat(browser.value("SESSION")).as("로그인하면 세션 ID가 새로 발급된다").isNotNull();
        if (before != null) {
            assertThat(browser.value("SESSION")).isNotEqualTo(before);
        }

        browser.perform(mvc, get("/api/me"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.nickname").value("빵순이"));

        browser.perform(mvc, browser.withCsrf(post("/api/logout"))).andExpect(status().isNoContent());
        browser.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void 비밀번호는_해시로만_저장된다() throws Exception {
        registered("hashcheck", "hash@example.com");

        String stored = jdbc.queryForObject("SELECT password_hash FROM local_credential WHERE login_id = 'hashcheck'", String.class);
        assertThat(stored).startsWith("{argon2}").doesNotContain(PASSWORD);
        String pendingLeft = jdbc.queryForObject("SELECT count(*)::text FROM pending_signup WHERE login_id = 'hashcheck'", String.class);
        assertThat(pendingLeft).isEqualTo("0");
    }

    @Test
    void 인증_토큰은_원문이_아니라_해시만_저장되고_한_번만_쓸_수_있다() throws Exception {
        signup("onetime1", "once@example.com", PASSWORD, "한번만").andExpect(status().isAccepted());
        String token = mailer.last().token();

        String stored = jdbc.queryForObject("SELECT token_hash FROM pending_signup WHERE login_id = 'onetime1'", String.class);
        assertThat(stored).hasSize(64).isNotEqualTo(token);

        verify(token).andExpect(status().isNoContent());
        verify(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(LocalAccountService.INVALID_LINK));
    }

    @Test
    void 만료된_토큰은_거부한다() throws Exception {
        signup("expired1", "exp@example.com", PASSWORD, "만료").andExpect(status().isAccepted());
        jdbc.update("UPDATE pending_signup SET expires_at = now() - interval '1 minute' WHERE login_id = 'expired1'");

        verify(mailer.last().token()).andExpect(status().isBadRequest());
        login("expired1", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void 같은_이메일로_다시_요청하면_이전_링크는_무효다() throws Exception {
        signup("retry001", "retry@example.com", PASSWORD, "재요청").andExpect(status().isAccepted());
        String first = mailer.last().token();
        signup("retry001", "retry@example.com", PASSWORD, "재요청").andExpect(status().isAccepted());
        String second = mailer.last().token();
        assertThat(second).isNotEqualTo(first);

        verify(first).andExpect(status().isBadRequest());
        verify(second).andExpect(status().isNoContent());
    }

    @Test
    void 이미_사용_중인_아이디는_409다() throws Exception {
        registered("taken001", "t1@example.com");

        signup("TAKEN001", "t2@example.com", PASSWORD, "다른사람").andExpect(status().isConflict());
        mvc.perform(get("/api/auth/id-available").param("loginId", "Taken001")).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/auth/id-available").param("loginId", "free0001")).andExpect(jsonPath("$.available").value(true));
        mvc.perform(get("/api/auth/id-available").param("loginId", "ab")).andExpect(status().isBadRequest());
    }

    @Test
    void 인증을_기다리는_아이디도_사용_중으로_본다() throws Exception {
        signup("pending1", "p1@example.com", PASSWORD, "대기").andExpect(status().isAccepted());

        signup("pending1", "other@example.com", PASSWORD, "다른이").andExpect(status().isConflict());
        mvc.perform(get("/api/auth/id-available").param("loginId", "pending1")).andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void 이미_가입된_이메일이어도_응답은_똑같고_주인에게만_메일이_간다() throws Exception {
        registered("owner001", "owner@example.com");
        mailer.clear();

        String normal = signup("newuser1", "brand-new@example.com", PASSWORD, "새사람").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String existing = signup("another1", "OWNER@example.com", PASSWORD, "도전자").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(existing).isEqualTo(normal);
        assertThat(mailer.alreadyRegistered).containsExactly("owner@example.com");
        assertThat(mailer.verifications).hasSize(1);
        assertThat(mailer.verifications.get(0).email()).isEqualTo("brand-new@example.com");
        String pending = jdbc.queryForObject("SELECT count(*)::text FROM pending_signup WHERE login_id = 'another1'", String.class);
        assertThat(pending).isEqualTo("0");
    }

    @Test
    void 로그인_실패_메시지는_없는_아이디와_틀린_비밀번호가_같다() throws Exception {
        registered("fail0001", "f@example.com");

        String wrongPassword = login("fail0001", "Wrong-Password-1!").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login("nobody001", "Wrong-Password-1!").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownUser).contains(LocalAuthController.LOGIN_FAILED);
    }

    @Test
    void 규칙에_맞지_않는_가입은_400과_이유를_돌려준다() throws Exception {
        signup("ab", "a@example.com", PASSWORD, "닉네임").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("아이디")));
        signup("validid1", "not-an-email", PASSWORD, "닉네임").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("이메일")));
        signup("validid1", "a@example.com", "short1!", "닉네임").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("비밀번호")));
        signup("validid1", "a@example.com", "onlyletters!!!", "닉네임").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("영문, 숫자, 특수문자")));
        signup("validid1", "a@example.com", PASSWORD, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("닉네임")));
        signupRaw("{\"loginId\":\"validid1\",\"email\":\"a@example.com\",\"password\":\"" + PASSWORD
                + "\",\"nickname\":\"닉네임\",\"agreedToTerms\":false}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("동의")));
        assertThat(mailer.verifications).isEmpty();
    }

    @Test
    void CSRF_토큰이_없으면_가입과_로그인_인증이_모두_403이다() throws Exception {
        String json = "{\"loginId\":\"csrfuser1\",\"email\":\"c@example.com\",\"password\":\"" + PASSWORD
                + "\",\"nickname\":\"닉네임\",\"agreedToTerms\":true}";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"x\",\"password\":\"y\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    void 아이디나_비밀번호가_비어_있으면_400이다() throws Exception {
        mvc.perform(withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(withCsrf(post("/api/auth/verify-email")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        return builder.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }

    private void registered(String loginId, String email) throws Exception {
        signup(loginId, email, PASSWORD, "테스트").andExpect(status().isAccepted());
        verify(mailer.last().token()).andExpect(status().isNoContent());
    }

    private ResultActions signup(String loginId, String email, String password, String nickname) throws Exception {
        return signupRaw(String.format("{\"loginId\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\",\"agreedToTerms\":true}",
                loginId, email, password, nickname));
    }

    private ResultActions signupRaw(String json) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions verify(String token) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/verify-email")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private ResultActions login(String loginId, String password) throws Exception {
        return login(loginId, password, new CookieSession(xsrf));
    }

    private ResultActions login(String loginId, String password, CookieSession browser) throws Exception {
        return browser.perform(mvc, browser.withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
    }
}
