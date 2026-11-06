package com.bigbread.api.admin;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code /api/admin/**}는 로그인한 관리자만 쓸 수 있다. 역할은 세션이 아니라 <b>요청마다 DB에서</b> 확인한다.
 * 그래서 DB에서 관리자로 지정하면 다시 로그인하지 않아도 바로 쓸 수 있고, 권한을 거두거나 계정을 정지하면 즉시 막힌다.
 */
@Component
class AdminAccess implements HandlerInterceptor {

    private final JdbcClient jdbc;

    AdminAccess(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw ApiException.unauthorized("로그인이 필요해요.");
        }
        boolean admin = jdbc.sql("SELECT EXISTS (SELECT 1 FROM app_user WHERE id = :id AND role = 'ADMIN' AND suspended_at IS NULL)")
                .param("id", principal.userId()).query(Boolean.class).single();
        if (!admin) {
            throw ApiException.forbidden("관리자만 쓸 수 있어요.");
        }
        return true;
    }

    @Configuration
    static class Registration implements WebMvcConfigurer {

        private final AdminAccess access;

        Registration(AdminAccess access) {
            this.access = access;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(access).addPathPatterns("/api/admin/**");
        }
    }
}
