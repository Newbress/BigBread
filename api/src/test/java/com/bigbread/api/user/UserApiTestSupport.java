package com.bigbread.api.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.auth.OAuthProfile;
import com.bigbread.api.auth.OAuthProvider;
import com.bigbread.api.auth.UserAccountService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인이 필요한 사용자 기능 테스트의 공통 도구. 실제 PostGIS DB가 필요하고 각 테스트는 롤백된다.
 * 로그인은 테스트용 인증 정보로 흉내 내고, 변경 요청은 프론트와 같이 CSRF 쿠키를 받아 헤더로 돌려보낸다.
 */
@SpringBootTest(properties = "bigbread.kakao.rest-api-key=")
@AutoConfigureMockMvc
@Transactional
public abstract class UserApiTestSupport {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected UserAccountService accounts;

    @Autowired
    protected JdbcTemplate jdbc;

    private Cookie xsrf;

    @BeforeEach
    void csrfCookie() throws Exception {
        xsrf = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    protected long newUser(String nickname) {
        return accounts.loginOrRegister(OAuthProvider.KAKAO,
                new OAuthProfile("test-" + SEQ.incrementAndGet() + "-" + System.nanoTime(), nickname, null, null));
    }

    /** 이 사용자로 로그인한 상태로 요청한다. */
    protected RequestPostProcessor as(long userId) {
        AppUserPrincipal principal = new AppUserPrincipal(userId, "USER");
        TestingAuthenticationToken token = new TestingAuthenticationToken(principal, "n/a", List.of());
        token.setAuthenticated(true);
        return authentication(token);
    }

    protected <B extends AbstractMockHttpServletRequestBuilder<B>> B withCsrf(B builder) {
        return builder.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }

    /** 대전 밖 가상 기준점 주변에 빵집을 만든다. */
    protected long newBakery(String name, String status) {
        Long id = jdbc.queryForObject("""
                INSERT INTO bakery (name, district_id, location, popularity_score, status)
                VALUES (?, 3, ST_SetSRID(ST_MakePoint(127.0, 36.0), 4326), 50, ?) RETURNING id
                """, Long.class, name, status);
        return id;
    }
}
