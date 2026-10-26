package com.bigbread.api.auth.local;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
class LocalAuthConfig {

    /**
     * 비밀번호 해시는 Argon2id가 기본이고, 저장값 앞에 {id}가 붙어서 나중에 알고리즘을 바꿔도 기존 사용자가 로그인된다
     * (로그인할 때 자동으로 새 알고리즘으로 갱신).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        return new DelegatingPasswordEncoder("argon2", encoders);
    }

    /** 로그인 성공 뒤 세션 ID를 새로 발급해 세션 고정 공격을 막는다. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    /** 로그인한 사용자를 HTTP 세션에 저장한다 (소셜 로그인과 같은 저장 방식). */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
