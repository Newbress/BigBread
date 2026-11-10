package com.bigbread.api.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bigbread.api.auth.CookieSession;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
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

/** 휴대폰 본인확인(PASS) 가입·비밀번호 재설정·아이디 찾기 (가상 제공자). */
@SpringBootTest(properties = {"bigbread.auth.signup-mode=pass", "bigbread.identity.provider=fake",
        "bigbread.identity.di-pepper=test-pepper-test-pepper-test-pepper-1", "bigbread.kakao.rest-api-key="})
@AutoConfigureMockMvc
@Transactional
class PassIdentityTest {

    private static final String PASSWORD = "BreadLover7!x";
    private static final String NEW_PASSWORD = "Fresh-Start#2025";
    private static final String ADULT = LocalDate.now().minusYears(30).toString();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuthRateLimits limits;

    private Cookie xsrf;
    private int seq;

    @BeforeEach
    void setUp() throws Exception {
        limits.reset();
        xsrf = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    void 화면에_알리는_가입_방식() throws Exception {
        mvc.perform(get("/api/auth/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.signupEnabled").value(true))
                .andExpect(jsonPath("$.collectEmail").value(false))
                .andExpect(jsonPath("$.verifyEmail").value(false))
                .andExpect(jsonPath("$.recoveryEnabled").value(false))
                .andExpect(jsonPath("$.verifyIdentity").value(true))
                .andExpect(jsonPath("$.identityProvider").value("fake"));
    }

    @Test
    void 본인확인을_마치면_가입되고_연계정보는_해시만_저장한다() throws Exception {
        String ticket = ticket("SIGNUP", "di-person-1", ADULT);
        CookieSession browser = new CookieSession(xsrf);
        signup(browser, ticket, "passuser1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.authenticated").value(true));

        String stored = jdbc.queryForObject("SELECT di_hash FROM local_credential WHERE login_id = 'passuser1'", String.class);
        assertThat(stored).hasSize(64).doesNotContain("di-person-1");
        assertThat(jdbc.queryForObject("SELECT email FROM local_credential WHERE login_id = 'passuser1'", String.class)).isNull();
        // 확인표는 한 번만 쓸 수 있다
        signup(new CookieSession(xsrf), ticket, "passuser2").andExpect(status().isBadRequest());
    }

    @Test
    void 확인표가_없거나_용도가_다르면_가입할_수_없다() throws Exception {
        signup(new CookieSession(xsrf), null, "passuser3").andExpect(status().isBadRequest());
        signup(new CookieSession(xsrf), "nope", "passuser3").andExpect(status().isBadRequest());
        String resetTicket = ticket("RESET", "di-person-2", ADULT);
        signup(new CookieSession(xsrf), resetTicket, "passuser3").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM local_credential WHERE login_id = 'passuser3'", Long.class)).isZero();
    }

    @Test
    void 만_14세_미만은_가입할_수_없고_같은_사람은_두_번_가입할_수_없다() throws Exception {
        verify("SIGNUP", "fake:di-kid:" + LocalDate.now().minusYears(13)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("14세")));

        signup(new CookieSession(xsrf), ticket("SIGNUP", "di-person-3", ADULT), "passuser4").andExpect(status().isCreated());
        verify("SIGNUP", "fake:di-person-3:" + ADULT).andExpect(status().isConflict());
    }

    @Test
    void 같은_인증_결과로_확인표를_두_번_받을_수_없다() throws Exception {
        String id = "fake:di-person-4:" + ADULT;
        verify("SIGNUP", id).andExpect(status().isOk());
        verify("SIGNUP", id).andExpect(status().isBadRequest());
        verify("SIGNUP", "fake:").andExpect(status().isBadRequest());
    }

    @Test
    void 이미_쓰는_아이디면_거부한다() throws Exception {
        signup(new CookieSession(xsrf), ticket("SIGNUP", "di-person-5", ADULT), "passuser5").andExpect(status().isCreated());
        signup(new CookieSession(xsrf), ticket("SIGNUP", "di-person-6", ADULT), "passuser5").andExpect(status().isConflict());
        // (확인표 사용 표시는 계정 생성과 한 트랜잭션이라, 운영에서는 실패하면 함께 되돌아가 확인표를 다시 쓸 수 있다)
    }

    @Test
    void 본인확인으로_비밀번호를_다시_정하면_다른_기기_로그인은_풀리고_새_비밀번호로_로그인된다() throws Exception {
        CookieSession first = new CookieSession(xsrf);
        signup(first, ticket("SIGNUP", "di-person-7", ADULT), "passuser7").andExpect(status().isCreated());
        first.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(true));

        String reset = ticket("RESET", "di-person-7", ADULT);
        resetByIdentity(reset, "PassUser7", NEW_PASSWORD).andExpect(status().isNoContent());

        first.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(false));
        login("passuser7", PASSWORD).andExpect(status().isUnauthorized());
        login("passuser7", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 다른_사람의_본인확인으로는_비밀번호를_바꿀_수_없고_이유도_구분되지_않는다() throws Exception {
        signup(new CookieSession(xsrf), ticket("SIGNUP", "di-owner", ADULT), "passuser8").andExpect(status().isCreated());

        String stranger = ticket("RESET", "di-stranger", ADULT);
        String mismatch = resetByIdentity(stranger, "passuser8", NEW_PASSWORD).andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        String missing = resetByIdentity(ticket("RESET", "di-stranger2", ADULT), "nobodyhere", NEW_PASSWORD)
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(mismatch, "$.detail")).isEqualTo(JsonPath.<String>read(missing, "$.detail"));
        login("passuser8", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 규칙에_맞지_않는_새_비밀번호는_거부한다() throws Exception {
        signup(new CookieSession(xsrf), ticket("SIGNUP", "di-person-9", ADULT), "passuser9").andExpect(status().isCreated());
        resetByIdentity(ticket("RESET", "di-person-9", ADULT), "passuser9", "short1!").andExpect(status().isBadRequest());
        login("passuser9", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 본인확인으로_아이디를_찾는다() throws Exception {
        signup(new CookieSession(xsrf), ticket("SIGNUP", "di-person-10", ADULT), "passuser10").andExpect(status().isCreated());
        mvc.perform(withCsrf(post("/api/auth/find-id-by-identity")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"" + ticket("FIND_ID", "di-person-10", ADULT) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.loginId").value("passuser10"));
        mvc.perform(withCsrf(post("/api/auth/find-id-by-identity")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"" + ticket("FIND_ID", "di-nobody", ADULT) + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 이메일_복구와_인증_메일_가입은_쓸_수_없다() throws Exception {
        mvc.perform(withCsrf(post("/api/auth/password/forgot")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"passuser1\"}")).andExpect(status().isServiceUnavailable());
        mvc.perform(withCsrf(post("/api/auth/find-id")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.com\"}")).andExpect(status().isServiceUnavailable());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCsrf(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }

    private ResultActions verify(String purpose, String verificationId) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/identity")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"" + purpose + "\",\"verificationId\":\"" + verificationId + "\"}"));
    }

    /** 새 사람(인증 ID 가 매번 다르다)으로 인증하고 확인표를 받는다. */
    private String ticket(String purpose, String di, String birth) throws Exception {
        // 실제 인증처럼 인증 ID 가 매번 다르도록 끝에 번호를 붙인다.
        String body = verify(purpose, "fake:" + di + ":" + birth + ":" + (seq++)).andReturn().getResponse().getContentAsString();
        if (!body.contains("ticket")) {
            throw new AssertionError("확인표를 받지 못했어요: " + body);
        }
        return JsonPath.read(body, "$.ticket");
    }

    private ResultActions signup(CookieSession browser, String ticket, String loginId) throws Exception {
        String ticketJson = ticket == null ? "" : ",\"identityTicket\":\"" + ticket + "\"";
        return browser.perform(mvc, browser.withCsrf(post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD
                        + "\",\"nickname\":\"본인확인손님\",\"agreedToTerms\":true" + ticketJson + "}"));
    }

    private ResultActions resetByIdentity(String ticket, String loginId, String newPassword) throws Exception {
        return mvc.perform(withCsrf(post("/api/auth/password/reset-by-identity")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticket\":\"" + ticket + "\",\"loginId\":\"" + loginId + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private ResultActions login(String loginId, String password) throws Exception {
        CookieSession browser = new CookieSession(xsrf);
        return browser.perform(mvc, browser.withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
    }
}
