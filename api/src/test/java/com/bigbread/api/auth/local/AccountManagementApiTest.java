package com.bigbread.api.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.auth.CookieSession;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 내 계정: 비밀번호 변경과 회원 탈퇴. */
@SpringBootTest(properties = {"bigbread.auth.base-url=http://localhost:5173", "bigbread.kakao.rest-api-key="})
@AutoConfigureMockMvc
@Transactional
@Import(AccountRecoveryApiTest.MailConfig.class)
class AccountManagementApiTest {

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

    private long register(String loginId, String email) throws Exception {
        mvc.perform(post("/api/auth/signup").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"loginId\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"테스트\",\"agreedToTerms\":true}",
                                loginId, email, PASSWORD)))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/verify-email").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + mailer.last().token() + "\"}"))
                .andExpect(status().isNoContent());
        limits.reset();
        mailer.clear();
        return jdbc.queryForObject("SELECT user_id FROM local_credential WHERE login_id = ?", Long.class, loginId);
    }

    private CookieSession loginAs(String loginId, String password) throws Exception {
        CookieSession browser = new CookieSession(xsrf);
        login(loginId, password, browser).andExpect(status().isOk());
        return browser;
    }

    private ResultActions login(String loginId, String password, CookieSession browser) throws Exception {
        return browser.perform(mvc, browser.withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions changePassword(CookieSession browser, String current, String next) throws Exception {
        return browser.perform(mvc, browser.withCsrf(put("/api/my/account/password")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions withdraw(CookieSession browser, String body) throws Exception {
        return browser.perform(mvc, browser.withCsrf(delete("/api/my/account")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long socialUser(String nickname) {
        long id = jdbc.queryForObject("INSERT INTO app_user (nickname) VALUES (?) RETURNING id", Long.class, nickname);
        jdbc.update("INSERT INTO oauth_account (user_id, provider, provider_user_id) VALUES (?, 'KAKAO', ?)", id, "k" + id + System.nanoTime());
        return id;
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor as(long userId) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(new AppUserPrincipal(userId, "USER"), "n/a", List.of());
        token.setAuthenticated(true);
        return authentication(token);
    }

    private long bakery(String name) {
        return jdbc.queryForObject("""
                INSERT INTO bakery (name, district_id, location, status)
                VALUES (?, 3, ST_SetSRID(ST_MakePoint(127.0, 36.0), 4326), 'ACTIVE') RETURNING id
                """, Long.class, name);
    }

    // ---------- 비밀번호 변경 ----------

    @Test
    void 비로그인은_401이다() throws Exception {
        mvc.perform(put("/api/my/account/password").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/my/account").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/my/account")).andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀번호를_바꾸면_새_비밀번호로만_로그인되고_다른_기기_로그인은_끊긴다() throws Exception {
        long userId = register("changer01", "chg@example.com");
        CookieSession phone = loginAs("changer01", PASSWORD);
        CookieSession laptop = loginAs("changer01", PASSWORD);

        changePassword(phone, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        // 바꾼 기기는 그대로 로그인, 다른 기기는 로그아웃
        phone.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(true));
        laptop.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Long.class, String.valueOf(userId)))
                .isEqualTo(1);
        // 확인 메일이 간다
        assertThat(mailer.passwordChanged).containsExactly("chg@example.com");

        login("changer01", PASSWORD, new CookieSession(xsrf)).andExpect(status().isUnauthorized());
        login("changer01", NEW_PASSWORD, new CookieSession(xsrf)).andExpect(status().isOk());
    }

    @Test
    void 현재_비밀번호가_틀리거나_새_비밀번호가_규칙에_안_맞으면_바뀌지_않는다() throws Exception {
        register("badpass01", "bad@example.com");
        CookieSession me = loginAs("badpass01", PASSWORD);

        changePassword(me, "Wrong-Password#1", NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("현재 비밀번호가 맞지 않아요."));
        changePassword(me, PASSWORD, "short1!").andExpect(status().isBadRequest());
        changePassword(me, PASSWORD, "badpass01-Extra!").andExpect(status().isBadRequest()); // 아이디 포함
        changePassword(me, PASSWORD, PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("다른 비밀번호")));
        login("badpass01", PASSWORD, new CookieSession(xsrf)).andExpect(status().isOk());
        assertThat(mailer.passwordChanged).isEmpty();
    }

    @Test
    void 현재_비밀번호를_계속_틀리면_횟수_제한에_걸린다() throws Exception {
        register("limit0001", "lim@example.com");
        CookieSession me = loginAs("limit0001", PASSWORD);
        for (int i = 0; i < 5; i++) {
            changePassword(me, "Wrong-Password#1", NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        // 다섯 번 틀린 뒤에는 맞는 비밀번호도 잠시 막힌다 (무차별 대입 방지)
        changePassword(me, PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void 소셜_계정은_비밀번호를_바꿀_수_없다() throws Exception {
        long user = socialUser("소셜");
        mvc.perform(put("/api/my/account/password").with(as(user)).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"x\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/my/account").with(as(user)))
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.providers[0]").value("KAKAO"));
    }

    @Test
    void 계정_정보에는_비밀번호_유무와_아이디가_나온다() throws Exception {
        register("infoer01", "info@example.com");
        CookieSession me = loginAs("infoer01", PASSWORD);
        me.perform(mvc, get("/api/my/account"))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.loginId").value("infoer01"));
    }

    // ---------- 회원 탈퇴 ----------

    @Test
    void 탈퇴하면_계정과_쓴_글이_모두_지워지고_평점이_다시_계산된다() throws Exception {
        long userId = register("leaver001", "leave@example.com");
        long other = socialUser("남은사람");
        long b = bakery("탈퇴 빵집");
        jdbc.update("INSERT INTO review (bakery_id, user_id, rating, content) VALUES (?, ?, 5, '내 리뷰')", b, userId);
        jdbc.update("INSERT INTO review (bakery_id, user_id, rating, content) VALUES (?, ?, 3, '남의 리뷰')", b, other);
        long myReview = jdbc.queryForObject("SELECT id FROM review WHERE user_id = ?", Long.class, userId);
        jdbc.update("INSERT INTO review_photo (review_id, storage_key, width, height, byte_size, seq) VALUES (?, ?, 10, 10, 1, 1)", myReview, "a".repeat(32));
        jdbc.update("INSERT INTO favorite (user_id, bakery_id) VALUES (?, ?)", userId, b);
        long course = jdbc.queryForObject("INSERT INTO course (user_id, title, order_mode) VALUES (?, '코스', 'MANUAL') RETURNING id", Long.class, userId);
        jdbc.update("INSERT INTO course_stop (course_id, seq, bakery_id) VALUES (?, 1, ?)", course, b);
        jdbc.update("INSERT INTO bakery_report (user_id, kind, name, address) VALUES (?, 'NEW', '제보', '대전')", userId);
        jdbc.update("INSERT INTO content_report (reporter_id, target_type, target_id, reason) VALUES (?, 'REVIEW', ?, 'SPAM')", other, myReview);
        jdbc.queryForList("SELECT recalc_bakery_stats(?)", b);
        assertThat(jdbc.queryForObject("SELECT review_count FROM bakery WHERE id = ?", Integer.class, b)).isEqualTo(2);

        CookieSession me = loginAs("leaver001", PASSWORD);
        withdraw(me, "{\"password\":\"Wrong-Password#1\"}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Long.class, userId)).isEqualTo(1);

        withdraw(me, "{\"password\":\"" + PASSWORD + "\"}").andExpect(status().isNoContent());

        for (String table : List.of("app_user WHERE id", "local_credential WHERE user_id", "review WHERE user_id", "favorite WHERE user_id",
                "course WHERE user_id", "bakery_report WHERE user_id")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " = ?", Long.class, userId)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_photo WHERE review_id = ?", Long.class, myReview)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Long.class, String.valueOf(userId))).isZero();
        // 남은 사람의 리뷰는 그대로, 집계는 다시 계산된다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review WHERE user_id = ?", Long.class, other)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT review_count FROM bakery WHERE id = ?", Integer.class, b)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rating_avg FROM bakery WHERE id = ?", Double.class, b)).isEqualTo(3.0);
        // 지워진 글에 대한 신고는 처리됨으로 닫힌다
        assertThat(jdbc.queryForObject("SELECT status FROM content_report WHERE target_id = ?", String.class, myReview)).isEqualTo("ACTIONED");

        // 로그인 불가, 같은 아이디로 다시 가입할 수 있다
        login("leaver001", PASSWORD, new CookieSession(xsrf)).andExpect(status().isUnauthorized());
        me.perform(mvc, get("/api/me")).andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(get("/api/auth/id-available").param("loginId", "leaver001")).andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 소셜_계정은_확인_문구를_입력해야_탈퇴한다() throws Exception {
        long user = socialUser("떠나는사람");
        var request = delete("/api/my/account").with(as(user)).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON);
        mvc.perform(request.content("{}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/my/account").with(as(user)).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"안 할래요\"}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Long.class, user)).isEqualTo(1);

        mvc.perform(delete("/api/my/account").with(as(user)).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"탈퇴\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Long.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oauth_account WHERE user_id = ?", Long.class, user)).isZero();
    }

    @Test
    void 관리자와_정지된_계정은_탈퇴할_수_없다() throws Exception {
        long admin = socialUser("운영자");
        jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE id = ?", admin);
        long suspended = socialUser("정지됨");
        jdbc.update("UPDATE app_user SET suspended_at = now(), suspended_reason = '도배' WHERE id = ?", suspended);

        for (long user : new long[] {admin, suspended}) {
            mvc.perform(delete("/api/my/account").with(as(user)).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"탈퇴\"}")).andExpect(status().isBadRequest());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE id = ?", Long.class, user)).isEqualTo(1);
        }
    }
}
