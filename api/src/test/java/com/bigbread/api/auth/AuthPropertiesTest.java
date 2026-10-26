package com.bigbread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    @Test
    void 클라이언트_ID가_있는_제공자만_켜진다() {
        AuthProperties properties = new AuthProperties("http://localhost:5173/",
                new AuthProperties.Client("k", "ks"), new AuthProperties.Client("", ""), new AuthProperties.Client("g", null));

        assertThat(properties.enabledProviders()).containsExactly(OAuthProvider.KAKAO, OAuthProvider.GOOGLE);
        assertThat(properties.registrations()).extracting("registrationId").isEqualTo(List.of("kakao", "google"));
    }

    @Test
    void 설정이_없으면_모두_꺼지고_기본_주소를_쓴다() {
        AuthProperties properties = new AuthProperties(null, null, null, null);

        assertThat(properties.enabledProviders()).isEmpty();
        assertThat(properties.baseUrl()).isEqualTo("http://localhost:5173");
    }

    @Test
    void 주소_끝의_슬래시는_제거한다() {
        assertThat(new AuthProperties("https://bigbread.example/", null, null, null).baseUrl())
                .isEqualTo("https://bigbread.example");
    }
}
