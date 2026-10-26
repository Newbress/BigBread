package com.bigbread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;

/** 제공자 서버 호출은 가짜로 대신하고, 응답을 받은 뒤의 처리(사용자 연결, 세션에 저장할 주체)를 확인한다. */
@SpringBootTest
@Transactional
class AppOAuth2UserServiceTest {

    @Autowired
    UserAccountService accounts;

    private OAuth2UserRequest request(OAuthProvider provider) {
        ClientRegistration registration = provider.toRegistration("cid", "secret", "http://localhost:5173");
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value",
                Instant.now(), Instant.now().plusSeconds(3600));
        return new OAuth2UserRequest(registration, token);
    }

    private AppOAuth2UserService serviceReturning(Map<String, Object> attributes, String nameAttribute) {
        OAuth2User fromProvider = new DefaultOAuth2User(List.of(), attributes, nameAttribute);
        return new AppOAuth2UserService(req -> fromProvider, accounts);
    }

    @Test
    void 카카오_로그인이면_사용자를_만들고_ID와_역할만_가진_주체를_돌려준다() {
        AppOAuth2UserService service = serviceReturning(Map.of("id", 777L, "kakao_account",
                Map.of("profile", Map.of("nickname", "카카오빵순이"))), "id");

        OAuth2User principal = service.loadUser(request(OAuthProvider.KAKAO));

        assertThat(principal).isInstanceOf(AppUserPrincipal.class);
        AppUserPrincipal app = (AppUserPrincipal) principal;
        assertThat(app.role()).isEqualTo("USER");
        assertThat(app.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        // 제공자 원본 응답(개인정보)은 세션에 남기지 않는다.
        assertThat(app.getAttributes()).isEmpty();
        assertThat(accounts.find(app.userId()).orElseThrow().nickname()).isEqualTo("카카오빵순이");
    }

    @Test
    void 같은_계정으로_다시_로그인하면_같은_사용자다() {
        AppOAuth2UserService service = serviceReturning(Map.of("sub", "g-1", "name", "G"), "sub");

        long first = ((AppUserPrincipal) service.loadUser(request(OAuthProvider.GOOGLE))).userId();
        long second = ((AppUserPrincipal) service.loadUser(request(OAuthProvider.GOOGLE))).userId();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void 제공자_응답에_ID가_없으면_로그인을_거부한다() {
        AppOAuth2UserService service = serviceReturning(Map.of("name", "ID없음"), "name");

        assertThatThrownBy(() -> service.loadUser(request(OAuthProvider.GOOGLE)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
