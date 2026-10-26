package com.bigbread.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF 토큰은 값을 읽을 때에야 쿠키(XSRF-TOKEN)로 내려간다. 프론트가 첫 요청 뒤에 바로 쿠키를 읽을 수 있도록
 * 매 요청마다 토큰을 한 번 읽어 쿠키를 보장한다.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // CsrfFilter가 요청 속성 "_csrf"에 (값을 읽을 때 쿠키를 쓰는) 지연 토큰을 넣어 둔다.
        Object attribute = request.getAttribute("_csrf");
        if (attribute instanceof CsrfToken token) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
