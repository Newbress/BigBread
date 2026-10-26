package com.bigbread.api.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 카카오와 구글만 켜진 환경(네이버는 키 없음). 제공자 서버에는 접속하지 않는다. */
@SpringBootTest(properties = {
        "bigbread.auth.base-url=http://localhost:5173",
        "bigbread.auth.kakao.client-id=test-kakao-id",
        "bigbread.auth.kakao.client-secret=test-kakao-secret",
        "bigbread.auth.google.client-id=test-google-id",
        "bigbread.auth.google.client-secret=test-google-secret",
        "bigbread.auth.naver.client-id=",
        "bigbread.kakao.rest-api-key="
})
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserAccountService accounts;

    @Test
    void 키가_설정된_제공자만_목록에_나온다() throws Exception {
        mvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("kakao"))
                .andExpect(jsonPath("$[0].name").value("카카오"))
                .andExpect(jsonPath("$[0].loginUrl").value("/oauth2/authorization/kakao"))
                .andExpect(jsonPath("$[1].id").value("google"));
    }

    @Test
    void 카카오_로그인_주소는_카카오_인증_페이지로_보낸다() throws Exception {
        mvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://kauth.kakao.com/oauth/authorize")))
                .andExpect(header().string("Location", containsString("client_id=test-kakao-id")))
                .andExpect(header().string("Location", containsString("response_type=code")))
                .andExpect(header().string("Location",
                        containsString("redirect_uri=http://localhost:5173/login/oauth2/code/kakao")))
                .andExpect(header().string("Location", containsString("state=")))
                // 비밀 키는 주소에 실리면 안 된다.
                .andExpect(header().string("Location", org.hamcrest.Matchers.not(containsString("test-kakao-secret"))));
    }

    @Test
    void 로그인하지_않아도_me는_오류_없이_비로그인으로_답한다() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").value(nullValue()));
    }

    @Test
    void 로그인한_사용자는_me로_자기_정보를_받는다() throws Exception {
        long userId = accounts.loginOrRegister(OAuthProvider.KAKAO,
                new OAuthProfile("me-1", "내정보", "me@example.com", "https://img/me.png"));

        mvc.perform(get("/api/me").with(authentication(login(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(jsonPath("$.user.nickname").value("내정보"))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void 삭제된_사용자의_세션은_비로그인으로_본다() throws Exception {
        mvc.perform(get("/api/me").with(authentication(login(-5))))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void 로그인이_필요한_경로는_비로그인이면_401이다() throws Exception {
        mvc.perform(get("/api/my/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void 조회_API와_동선_계산은_로그인_없이_쓸_수_있다() throws Exception {
        mvc.perform(get("/api/districts")).andExpect(status().isOk());
        // CSRF 토큰 없이도 동선 계산(저장 없음)은 허용된다. 빵집이 없으면 400이지만 403은 아니다.
        mvc.perform(post("/api/routes").contentType(MediaType.APPLICATION_JSON).content("{\"bakeryIds\":[999999999]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 응답에_CSRF_쿠키가_내려가고_읽을_수_있다() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void 로그아웃은_쿠키로_받은_토큰을_헤더로_돌려보내야_성공한다() throws Exception {
        // 프론트와 같은 흐름: 아무 GET으로 XSRF-TOKEN 쿠키를 받고, 그 값을 X-XSRF-TOKEN 헤더로 보낸다.
        jakarta.servlet.http.Cookie xsrf = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(post("/api/logout")).andExpect(status().isForbidden());
        mvc.perform(post("/api/logout").cookie(xsrf).header("X-XSRF-TOKEN", "다른-값")).andExpect(status().isForbidden());
        mvc.perform(post("/api/logout").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
    }

    private static Authentication login(long userId) {
        AppUserPrincipal principal = new AppUserPrincipal(userId, "USER");
        TestingAuthenticationToken token = new TestingAuthenticationToken(principal, "n/a", List.of());
        token.setAuthenticated(true);
        return token;
    }
}
