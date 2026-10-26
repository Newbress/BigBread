package com.bigbread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가짜 제공자 서버(토큰·사용자 정보)를 띄워 로그인 전체 흐름을 끝까지 실행한다.
 * 인가 요청 → 콜백(code, state) → 토큰 교환 → 사용자 정보 → 세션 → /api/me → 로그아웃.
 */
@SpringBootTest(properties = {
        "bigbread.auth.base-url=http://localhost:5173",
        "bigbread.auth.kakao.client-id=flow-kakao-id",
        "bigbread.auth.kakao.client-secret=flow-kakao-secret",
        "bigbread.kakao.rest-api-key="
})
@AutoConfigureMockMvc
@Transactional
@Import(OAuthLoginFlowTest.FakeProviderConfig.class)
class OAuthLoginFlowTest {

    private static HttpServer fakeProvider;
    private static final AtomicReference<String> tokenRequestBody = new AtomicReference<>();
    private static final AtomicReference<String> userInfoAuthorization = new AtomicReference<>();

    @BeforeAll
    static void startFakeProvider() throws IOException {
        fakeProvider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeProvider.createContext("/token", exchange -> {
            tokenRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"access_token\":\"fake-access\",\"token_type\":\"bearer\",\"expires_in\":3600}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeProvider.createContext("/userinfo", exchange -> {
            userInfoAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"id\":424242,\"kakao_account\":{\"email\":\"flow@example.com\","
                    + "\"profile\":{\"nickname\":\"흐름테스트\",\"profile_image_url\":\"https://img/flow.png\"}}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeProvider.start();
    }

    @AfterAll
    static void stopFakeProvider() {
        fakeProvider.stop(0);
    }

    /** 카카오 등록 정보의 토큰·사용자 정보 주소만 가짜 서버로 바꾼 저장소를 쓴다. */
    @TestConfiguration
    static class FakeProviderConfig {
        @Bean
        @Primary
        ConfiguredClientRegistrationRepository fakeRegistrations() {
            // 정적 서버는 @BeforeAll에서 이미 떠 있다.
            String base = "http://127.0.0.1:" + fakeProvider.getAddress().getPort();
            ClientRegistration kakao = ClientRegistration
                    .withClientRegistration(OAuthProvider.KAKAO.toRegistration("flow-kakao-id", "flow-kakao-secret",
                            "http://localhost:5173"))
                    .tokenUri(base + "/token")
                    .userInfoUri(base + "/userinfo")
                    .build();
            return new ConfiguredClientRegistrationRepository(List.of(kakao));
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void 로그인부터_로그아웃까지_전체_흐름이_동작한다() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1) 로그인 시작: 제공자 인증 페이지로 보내면서 state를 세션에 저장한다.
        MvcResult start = mvc.perform(get("/oauth2/authorization/kakao").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = start.getResponse().getHeader("Location");
        String state = queryParam(location, "state");
        assertThat(state).isNotBlank();

        // 2) 제공자가 code와 state를 붙여 돌려보낸다.
        mvc.perform(get("/login/oauth2/code/kakao").param("code", "fake-code").param("state", state).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:5173/"));

        // 서버가 코드를 토큰으로 바꿀 때 클라이언트 ID/비밀을 본문으로 보냈고, 사용자 정보는 받은 토큰으로 요청했다.
        assertThat(tokenRequestBody.get()).contains("grant_type=authorization_code").contains("code=fake-code")
                .contains("client_id=flow-kakao-id").contains("client_secret=flow-kakao-secret");
        assertThat(userInfoAuthorization.get()).isEqualTo("Bearer fake-access");

        // 3) 같은 세션으로 /api/me: 로그인된 사용자 정보가 나온다.
        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.nickname").value("흐름테스트"))
                .andExpect(jsonPath("$.user.email").value("flow@example.com"))
                .andExpect(jsonPath("$.user.profileImageUrl").value("https://img/flow.png"));

        // 로그인이 필요한 경로도 같은 세션이면 통과한다 (없는 경로라 404이지 401/403이 아니다).
        mvc.perform(get("/api/my/nothing").session(session)).andExpect(status().isNotFound());

        // 4) 로그아웃: CSRF 토큰을 돌려보내야 하고, 이후에는 비로그인이다.
        Cookie xsrf = mvc.perform(get("/api/me").session(session)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/logout").session(session).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void state가_맞지_않는_콜백은_로그인_실패로_돌려보낸다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/oauth2/authorization/kakao").session(session)).andExpect(status().is3xxRedirection());

        mvc.perform(get("/login/oauth2/code/kakao").param("code", "fake-code").param("state", "위조된-값")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:5173/?login=error"));
        mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void 사용자가_동의를_거부해_error로_돌아오면_로그인_실패로_돌려보낸다() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult start = mvc.perform(get("/oauth2/authorization/kakao").session(session)).andReturn();
        String state = queryParam(start.getResponse().getHeader("Location"), "state");

        mvc.perform(get("/login/oauth2/code/kakao").param("error", "access_denied").param("state", state)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost:5173/?login=error"));
    }

    private static String queryParam(String url, String name) {
        String query = URI.create(url).getRawQuery();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
