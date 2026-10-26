package com.bigbread.api.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 로그인한 사용자. 세션에 저장되므로 제공자가 준 원본 응답(토큰, 개인정보)은 들고 있지 않고 사용자 ID와 역할만 가진다.
 */
public record AppUserPrincipal(long userId, String role) implements OAuth2User, Serializable {

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
