package com.bigbread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

class OAuthProviderTest {

    @Test
    void 카카오_응답에서_ID_닉네임_이메일_사진을_꺼낸다() {
        Map<String, Object> attributes = Map.of(
                "id", 123456789L,
                "kakao_account", Map.of(
                        "email", "bread@example.com",
                        "profile", Map.of("nickname", "빵순이", "profile_image_url", "https://k.kakaocdn.net/p.jpg")));

        OAuthProfile profile = OAuthProvider.KAKAO.extract(attributes);

        assertThat(profile).isEqualTo(new OAuthProfile("123456789", "빵순이", "bread@example.com",
                "https://k.kakaocdn.net/p.jpg"));
    }

    @Test
    void 카카오는_kakao_account에_프로필이_없으면_properties를_쓴다() {
        Map<String, Object> attributes = Map.of("id", 1L,
                "properties", Map.of("nickname", "옛날형", "profile_image", "https://k/img.png"));

        OAuthProfile profile = OAuthProvider.KAKAO.extract(attributes);

        assertThat(profile.nickname()).isEqualTo("옛날형");
        assertThat(profile.profileImageUrl()).isEqualTo("https://k/img.png");
        assertThat(profile.email()).isNull();
    }

    @Test
    void 네이버는_response로_감싸진_응답을_읽는다() {
        Map<String, Object> attributes = Map.of("resultcode", "00", "response", Map.of(
                "id", "naver-abc", "nickname", "소금빵", "email", "n@example.com", "profile_image", "https://n/p.png"));

        OAuthProfile profile = OAuthProvider.NAVER.extract(attributes);

        assertThat(profile).isEqualTo(new OAuthProfile("naver-abc", "소금빵", "n@example.com", "https://n/p.png"));
    }

    @Test
    void 네이버는_닉네임이_없으면_이름을_쓴다() {
        Map<String, Object> attributes = Map.of("response", Map.of("id", "x", "name", "홍길동"));
        assertThat(OAuthProvider.NAVER.extract(attributes).nickname()).isEqualTo("홍길동");
    }

    @Test
    void 구글은_sub를_ID로_쓴다() {
        Map<String, Object> attributes = Map.of("sub", "1098765", "name", "Bread Fan", "email", "g@example.com",
                "picture", "https://lh3.googleusercontent.com/a");

        OAuthProfile profile = OAuthProvider.GOOGLE.extract(attributes);

        assertThat(profile).isEqualTo(new OAuthProfile("1098765", "Bread Fan", "g@example.com",
                "https://lh3.googleusercontent.com/a"));
    }

    @Test
    void 응답에_값이_없어도_예외_없이_빈_값이다() {
        for (OAuthProvider provider : OAuthProvider.values()) {
            OAuthProfile profile = provider.extract(Map.of());
            assertThat(profile.providerUserId()).isNull();
        }
    }

    @Test
    void 정리하면_닉네임_기본값_길이_제한_안전한_사진_URL만_남는다() {
        OAuthProfile cleaned = OAuthProvider.sanitize(new OAuthProfile("1", "  ", " a@b.c ", "javascript:alert(1)"));
        assertThat(cleaned.nickname()).isEqualTo("빵순이");
        assertThat(cleaned.email()).isEqualTo("a@b.c");
        assertThat(cleaned.profileImageUrl()).isNull();

        OAuthProfile longName = OAuthProvider.sanitize(new OAuthProfile("1", "가".repeat(80), null, null));
        assertThat(longName.nickname()).hasSize(OAuthProvider.NICKNAME_MAX);

        OAuthProfile longEmail = OAuthProvider.sanitize(new OAuthProfile("1", "n", "a".repeat(300) + "@x.com", null));
        assertThat(longEmail.email()).isNull();
    }

    @Test
    void 등록_정보에_리다이렉트_URI와_엔드포인트가_들어간다() {
        ClientRegistration kakao = OAuthProvider.KAKAO.toRegistration("kid", "ksecret", "http://localhost:5173");

        assertThat(kakao.getRegistrationId()).isEqualTo("kakao");
        assertThat(kakao.getRedirectUri()).isEqualTo("http://localhost:5173/login/oauth2/code/kakao");
        assertThat(kakao.getProviderDetails().getAuthorizationUri()).isEqualTo("https://kauth.kakao.com/oauth/authorize");
        assertThat(kakao.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("id");

        ClientRegistration naver = OAuthProvider.NAVER.toRegistration("nid", "nsecret", "https://bigbread.example");
        assertThat(naver.getRedirectUri()).isEqualTo("https://bigbread.example/login/oauth2/code/naver");
        assertThat(naver.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("response");
    }

    @Test
    void 등록_ID로_제공자를_찾고_모르는_ID는_거부한다() {
        assertThat(OAuthProvider.fromRegistrationId("google")).isEqualTo(OAuthProvider.GOOGLE);
        assertThatThrownBy(() -> OAuthProvider.fromRegistrationId("facebook")).isInstanceOf(IllegalArgumentException.class);
    }
}
