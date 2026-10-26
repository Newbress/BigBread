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

/** 비밀번호 재설정, 아이디 찾기, 로그인 잠금, 요청 제한, 세션 저장·만료. */
@SpringBootTest(properties = {"bigbread.auth.base-url=http://localhost:5173", "bigbread.kakao.rest-api-key="})
@AutoConfigureMockMvc
@Transactional
@Import(AccountRecoveryApiTest.MailConfig.class)
class AccountRecoveryApiTest {

    @TestConfiguration
    static class MailConfig {
        @Bean
        @Primary
        CapturingAuthMailer capturingAuthMailer() {
            return new CapturingAuthMailer();
        }
    }

    private static final String PASSWORD = "BreadLover7!x";
    private static final String NEW_PASSWORD = "Fresh-Start#2025";

    @Autowired
    MockMvc mvc;

    @Autowired
    CapturingAuthMailer mailer;

    @Autowired
    AuthRateLimits limits;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie xsrf;

    @BeforeEach
    void setUp() throws Exception {
        mailer.clear();
        limits.reset();
        xsrf = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    // ---------- 비밀번호 재설정 ----------

    @Test
    void 재설정_링크를_받아_새_비밀번호로_바꾸면_새_비밀번호로만_로그인된다() throws Exception {
        register("resetme1", "reset@example.com");
        mailer.clear();

        forgot("ResetMe1").andExpect(status().isAccepted());
        assertThat(mailer.passwordResets).hasSize(1);
        CapturingAuthMailer.Verification mail = mailer.lastReset();
        assertThat(mail.email()).isEqualTo("reset@example.com");
        assertThat(mail.link()).startsWith("http://localhost:5173/reset-password?token=");
        assertThat(mail.validFor().toMinutes()).isEqualTo(30);
        String storedHash = jdbc.queryForObject("SELECT token_hash FROM auth_token WHERE purpose = 'PASSWORD_RESET'", String.class);
        assertThat(storedHash).hasSize(64).isNotEqualTo(mail.token());

        reset(mail.token(), NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(mailer.passwordChanged).containsExactly("reset@example.com");

        login("resetme1", PASSWORD, new CookieSession(xsrf)).andExpect(status().isUnauthorized());
        login("resetme1", NEW_PASSWORD, new CookieSession(xsrf)).andExpect(status().isOk());
    }

    @Test
    void 가입되지_않은_아이디도_응답이_똑같고_메일은_가지_않는다() throws Exception {
        register("exists01", "e@example.com");
        mailer.clear();

        String known = forgot("exists01").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = forgot("nobody01").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(known);
        assertThat(mailer.passwordResets).hasSize(1);
    }

    @Test
    void 규칙에_맞지_않는_새_비밀번호는_거부하고_링크는_그대로_쓸_수_있다() throws Exception {
        register("weakpass1", "w@example.com");
        mailer.clear();
        forgot("weakpass1");
        String token = mailer.lastReset().token();

        reset(token, "short1!").andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("비밀번호")));
        reset(token, "Password1234!").andExpect(status().isBadRequest());
        reset(token, "My-weakpass1-Pass9!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("비밀번호에 아이디를 포함할 수 없어요."));

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void 재설정_링크는_한_번만_쓸_수_있다() throws Exception {
        register("onetime2", "o@example.com");
        mailer.clear();
        forgot("onetime2");
        String token = mailer.lastReset().token();

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(token, "Another-Pass#2026").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(AccountRecoveryService.INVALID_RESET_LINK));
        login("onetime2", NEW_PASSWORD, new CookieSession(xsrf)).andExpect(status().isOk());
    }

    @Test
    void 만료된_링크는_거부한다() throws Exception {
        register("expire02", "x@example.com");
        mailer.clear();
        forgot("expire02");
        jdbc.update("UPDATE auth_token SET expires_at = now() - interval '1 minute'");

        reset(mailer.lastReset().token(), NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void 새로_요청하면_이전_링크는_무효가_된다() throws Exception {
        register("again001", "a@example.com");
        mailer.clear();
        forgot("again001");
        String first = mailer.lastReset().token();
        forgot("again001");
        String second = mailer.lastReset().token();

        reset(first, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void 알_수_없는_토큰은_400이다() throws Exception {
        reset("not-a-real-token", NEW_PASSWORD).andExpect(status().isBadRequest());
        mvc.perform(withCsrf(post("/api/auth/password/reset")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 같은_아이디로_재설정을_너무_자주_요청하면_조용히_메일을_보내지_않는다() throws Exception {
        register("spam0001", "s@example.com");
        mailer.clear();

        for (int i = 0; i < 4; i++) {
            forgot("spam0001").andExpect(status().isAccepted()); // 응답은 항상 같다
        }
        assertThat(mailer.passwordResets).as("대상별 한도(1시간 3번)를 넘으면 메일을 보내지 않는다").hasSize(3);
    }

    @Test
    void 같은_IP에서_재설정을_너무_자주_요청하면_429다() throws Exception {
        for (int i = 0; i < 5; i++) {
            forgot("whoever" + i).andExpect(status().isAccepted());
        }
        forgot("whoever9").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(containsString("요청이 너무 많아요")));
    }

    // ---------- 세션 ----------

    @Test
    void 로그인_세션은_DB에_저장되고_비밀번호를_바꾸면_모든_기기의_세션이_끊긴다() throws Exception {
        long userId = register("session01", "sess@example.com");
        CookieSession phone = new CookieSession(xsrf);
        CookieSession laptop = new CookieSession(xsrf);
        login("session01", PASSWORD, phone).andExpect(status().isOk());
        login("session01", PASSWORD, laptop).andExpect(status().isOk());

        // 세션이 DB에 저장되고, 사용자 ID로 찾을 수 있다.
        Long stored = jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Long.class, String.valueOf(userId));
        assertThat(stored).isEqualTo(2);
        phone.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(true));
        laptop.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(true));

        mailer.clear();
        forgot("session01");
        reset(mailer.lastReset().token(), NEW_PASSWORD).andExpect(status().isNoContent());

        Long left = jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Long.class, String.valueOf(userId));
        assertThat(left).as("비밀번호를 바꾸면 그 사용자의 세션이 모두 사라진다").isZero();
        phone.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(false));
        laptop.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void 다른_사용자의_세션은_영향받지_않는다() throws Exception {
        register("keeper01", "keep@example.com");
        register("changer1", "chg@example.com");
        CookieSession keeper = new CookieSession(xsrf);
        login("keeper01", PASSWORD, keeper).andExpect(status().isOk());

        mailer.clear();
        forgot("changer1");
        reset(mailer.lastReset().token(), NEW_PASSWORD).andExpect(status().isNoContent());

        keeper.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(true));
    }

    // ---------- 아이디 찾기 ----------

    @Test
    void 아이디_찾기는_가입된_이메일이면_메일로_아이디를_알려준다() throws Exception {
        register("findme01", "find@example.com");
        mailer.clear();

        String known = findId("FIND@example.com").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = findId("nobody@example.com").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(known);
        assertThat(mailer.loginIds).hasSize(1);
        assertThat(mailer.loginIds.get(0)).containsExactly("find@example.com", "findme01");
    }

    @Test
    void 아이디_찾기는_이메일_형식이_틀리면_400이다() throws Exception {
        findId("not-an-email").andExpect(status().isBadRequest());
    }

    @Test
    void 같은_이메일로_아이디_찾기를_너무_자주_요청하면_조용히_보내지_않는다() throws Exception {
        register("findspam", "fs@example.com");
        mailer.clear();
        for (int i = 0; i < 4; i++) {
            findId("fs@example.com").andExpect(status().isAccepted());
        }
        assertThat(mailer.loginIds).hasSize(3);
    }

    // ---------- 로그인 잠금 ----------

    @Test
    void 연속_5번_틀리면_잠기고_잠긴_동안은_맞는_비밀번호도_거부한다() throws Exception {
        register("lockme01", "lock@example.com");

        for (int i = 0; i < 5; i++) {
            login("lockme01", "Wrong-Password-" + i + "!", new CookieSession(xsrf)).andExpect(status().isUnauthorized());
        }
        login("lockme01", PASSWORD, new CookieSession(xsrf)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("로그인 시도가 너무 많아요. 15분 뒤에 다시 시도해 주세요."));
        Boolean locked = jdbc.queryForObject("SELECT locked_until > now() FROM local_credential WHERE login_id = 'lockme01'", Boolean.class);
        assertThat(locked).isTrue();
    }

    @Test
    void 잠금_시간이_지나면_다시_로그인된다() throws Exception {
        register("unlock01", "unlock@example.com");
        for (int i = 0; i < 5; i++) {
            login("unlock01", "Wrong-Password-" + i + "!", new CookieSession(xsrf));
        }
        jdbc.update("UPDATE local_credential SET locked_until = now() - interval '1 second' WHERE login_id = 'unlock01'");

        login("unlock01", PASSWORD, new CookieSession(xsrf)).andExpect(status().isOk());
        Long attempts = jdbc.queryForObject("SELECT failed_attempts FROM local_credential WHERE login_id = 'unlock01'", Long.class);
        assertThat(attempts).isZero();
    }

    @Test
    void 성공하면_실패_횟수가_지워져서_다시_5번까지_틀릴_수_있다() throws Exception {
        register("resetcnt", "rc@example.com");
        for (int i = 0; i < 4; i++) {
            login("resetcnt", "Wrong-Password-" + i + "!", new CookieSession(xsrf)).andExpect(status().isUnauthorized());
        }
        login("resetcnt", PASSWORD, new CookieSession(xsrf)).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login("resetcnt", "Wrong-Password-" + i + "!", new CookieSession(xsrf)).andExpect(status().isUnauthorized());
        }
        login("resetcnt", PASSWORD, new CookieSession(xsrf)).andExpect(status().isOk());
    }

    @Test
    void 한_계정이_잠겨도_다른_계정은_영향이_없다() throws Exception {
        register("victim01", "v@example.com");
        register("bystand1", "b@example.com");
        for (int i = 0; i < 5; i++) {
            login("victim01", "Wrong-Password-" + i + "!", new CookieSession(xsrf));
        }
        login("bystand1", PASSWORD, new CookieSession(xsrf)).andExpect(status().isOk());
    }

    @Test
    void 비밀번호를_재설정하면_잠금도_풀린다() throws Exception {
        register("locked02", "l2@example.com");
        for (int i = 0; i < 5; i++) {
            login("locked02", "Wrong-Password-" + i + "!", new CookieSession(xsrf));
        }
        login("locked02", PASSWORD, new CookieSession(xsrf)).andExpect(status().isTooManyRequests());

        mailer.clear();
        forgot("locked02");
        reset(mailer.lastReset().token(), NEW_PASSWORD).andExpect(status().isNoContent());

        login("locked02", NEW_PASSWORD, new CookieSession(xsrf)).andExpect(status().isOk());
    }

    @Test
    void 없는_아이디로_계속_시도해도_잠금_상태가_드러나지_않는다() throws Exception {
        for (int i = 0; i < 8; i++) {
            login("ghost001", "Wrong-Password-" + i + "!", new CookieSession(xsrf)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void 같은_IP에서_로그인을_너무_많이_시도하면_429다() throws Exception {
        for (int i = 0; i < 30; i++) {
            login("ghost" + (i % 5) + "01", "Wrong-Password-1!", new CookieSession(xsrf)).andExpect(status().isUnauthorized());
        }
        login("ghost0001", "Wrong-Password-1!", new CookieSession(xsrf)).andExpect(status().isTooManyRequests());
    }

    // ---------- helpers ----------

    private long register(String loginId, String email) throws Exception {
        mvc.perform(withCsrf(post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"loginId\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"테스트\",\"agreedToTerms\":true}",
                                loginId, email, PASSWORD)))
                .andExpect(status().isAccepted());
        mvc.perform(withCsrf(post("/api/auth/verify-email")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + mailer.last().token() + "\"}"))
                .andExpect(status().isNoContent());
        limits.reset(); // 준비 단계의 요청이 제한 횟수를 쓰지 않게
        return jdbc.queryForObject("SELECT user_id FROM local_credential WHERE login_id = ?", Long.class, loginId);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCsrf(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }

    private ResultActions forgot(String loginId) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/password/forgot")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\"}"));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/password/reset")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private ResultActions findId(String email) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/find-id")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions login(String loginId, String password, CookieSession browser) throws Exception {
        return browser.perform(mvc, browser.withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
    }
}
