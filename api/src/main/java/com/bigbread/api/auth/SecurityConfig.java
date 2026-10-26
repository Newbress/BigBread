package com.bigbread.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * 인증 설정. 로그인은 서버 세션(HttpOnly 쿠키)으로 유지한다.
 *
 * <ul>
 *   <li>조회 API는 모두 공개이고, 로그인이 필요한 API는 {@code /api/my/**} 아래에 둔다.</li>
 *   <li>POST/PUT/DELETE 같은 변경 요청은 CSRF 토큰(쿠키 XSRF-TOKEN → 헤더 X-XSRF-TOKEN)이 필요하다.
 *       {@code /api/routes}는 아무것도 저장하지 않는 계산이라 제외한다.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    ConfiguredClientRegistrationRepository clientRegistrationRepository(AuthProperties properties) {
        return new ConfiguredClientRegistrationRepository(properties.registrations());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties properties,
                                            ConfiguredClientRegistrationRepository registrations,
                                            UserAccountService accounts) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/my/**").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/routes"))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));

        if (!registrations.isEmpty()) {
            SimpleUrlAuthenticationFailureHandler failure =
                    new SimpleUrlAuthenticationFailureHandler(properties.baseUrl() + "/?login=error") {
                        @Override
                        public void onAuthenticationFailure(jakarta.servlet.http.HttpServletRequest request,
                                                            jakarta.servlet.http.HttpServletResponse response,
                                                            org.springframework.security.core.AuthenticationException exception)
                                throws java.io.IOException, jakarta.servlet.ServletException {
                            // 비밀 값은 남기지 않고 원인만 기록한다.
                            log.warn("소셜 로그인 실패: {}", exception.getMessage());
                            super.onAuthenticationFailure(request, response, exception);
                        }
                    };
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(registrations)
                    .userInfoEndpoint(user -> user.userService(new AppOAuth2UserService(accounts)))
                    .defaultSuccessUrl(properties.baseUrl() + "/", true)
                    .failureHandler(failure));
        }
        return http.build();
    }
}
