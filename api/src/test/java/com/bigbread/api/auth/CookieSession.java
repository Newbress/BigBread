package com.bigbread.api.auth;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 테스트용 브라우저 쿠키 보관함. 응답의 Set-Cookie를 기억했다가 다음 요청에 실어 보낸다.
 * 세션이 서버(DB)에 저장되고 쿠키(SESSION)로 식별되므로, 로그인 상태를 이어가려면 쿠키를 주고받아야 한다.
 */
public class CookieSession {

    private final Map<String, Cookie> cookies = new LinkedHashMap<>();

    public CookieSession(Cookie... initial) {
        for (Cookie cookie : initial) {
            cookies.put(cookie.getName(), cookie);
        }
    }

    public ResultActions perform(MockMvc mvc, MockHttpServletRequestBuilder builder) throws Exception {
        cookies.values().forEach(builder::cookie);
        ResultActions result = mvc.perform(builder);
        for (Cookie cookie : result.andReturn().getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0 || cookie.getValue() == null || cookie.getValue().isEmpty()) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie);
            }
        }
        return result;
    }

    /** 변경 요청에 쓸 CSRF 헤더를 붙인다 (XSRF-TOKEN 쿠키 값). */
    public MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        String token = value("XSRF-TOKEN");
        if (token != null) {
            builder.header("X-XSRF-TOKEN", token);
        }
        return builder;
    }

    public String value(String name) {
        Cookie cookie = cookies.get(name);
        return cookie == null ? null : cookie.getValue();
    }
}
