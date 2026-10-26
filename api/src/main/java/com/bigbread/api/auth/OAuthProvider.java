package com.bigbread.api.auth;

import java.util.Locale;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * 지원하는 소셜 로그인 제공자. 등록 정보(엔드포인트, 스코프)와 사용자 정보 응답 해석을 함께 둔다.
 * DB의 {@code oauth_account.provider} 값은 {@link #name()}이고, 주소와 등록 ID는 소문자({@link #registrationId()})다.
 */
public enum OAuthProvider {

    KAKAO("카카오") {
        @Override
        ClientRegistration.Builder registration(String clientId, String clientSecret, String redirectUri) {
            return base(clientId, clientSecret, redirectUri)
                    .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                    .tokenUri("https://kauth.kakao.com/oauth/token")
                    .userInfoUri("https://kapi.kakao.com/v2/user/me")
                    .userNameAttributeName("id")
                    // 이메일은 카카오 앱의 동의항목 설정이 필요해서 기본으로 요청하지 않는다.
                    .scope("profile_nickname", "profile_image");
        }

        @Override
        public OAuthProfile extract(Map<String, Object> attributes) {
            Map<String, Object> account = map(attributes.get("kakao_account"));
            Map<String, Object> profile = map(account.get("profile"));
            Map<String, Object> properties = map(attributes.get("properties"));
            return new OAuthProfile(
                    text(attributes.get("id")),
                    first(text(profile.get("nickname")), text(properties.get("nickname"))),
                    text(account.get("email")),
                    first(text(profile.get("profile_image_url")), text(properties.get("profile_image"))));
        }
    },

    NAVER("네이버") {
        @Override
        ClientRegistration.Builder registration(String clientId, String clientSecret, String redirectUri) {
            return base(clientId, clientSecret, redirectUri)
                    .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                    .tokenUri("https://nid.naver.com/oauth2.0/token")
                    .userInfoUri("https://openapi.naver.com/v1/nid/me")
                    // 네이버는 응답이 {"response": {...}} 로 감싸져 있다.
                    .userNameAttributeName("response");
        }

        @Override
        public OAuthProfile extract(Map<String, Object> attributes) {
            Map<String, Object> response = map(attributes.get("response"));
            return new OAuthProfile(
                    text(response.get("id")),
                    first(text(response.get("nickname")), text(response.get("name"))),
                    text(response.get("email")),
                    text(response.get("profile_image")));
        }
    },

    GOOGLE("구글") {
        @Override
        ClientRegistration.Builder registration(String clientId, String clientSecret, String redirectUri) {
            return base(clientId, clientSecret, redirectUri)
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .userNameAttributeName("sub")
                    .scope("profile", "email");
        }

        @Override
        public OAuthProfile extract(Map<String, Object> attributes) {
            return new OAuthProfile(
                    text(attributes.get("sub")),
                    text(attributes.get("name")),
                    text(attributes.get("email")),
                    text(attributes.get("picture")));
        }
    };

    private static final String DEFAULT_NICKNAME = "빵순이";
    static final int NICKNAME_MAX = 50;
    static final int EMAIL_MAX = 255;
    static final int IMAGE_URL_MAX = 500;

    private final String displayName;

    OAuthProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String registrationId() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 사용자 정보 응답에서 필요한 값만 꺼낸다. 값이 없거나 길이가 넘치면 DB 제약에 맞게 정리한다. */
    public abstract OAuthProfile extract(Map<String, Object> attributes);

    abstract ClientRegistration.Builder registration(String clientId, String clientSecret, String redirectUri);

    ClientRegistration toRegistration(String clientId, String clientSecret, String baseUrl) {
        String redirectUri = baseUrl + "/login/oauth2/code/" + registrationId();
        return registration(clientId, clientSecret, redirectUri).build();
    }

    /** 응답을 DB에 넣을 수 있는 값으로 정리한다. */
    public static OAuthProfile sanitize(OAuthProfile raw) {
        String nickname = raw.nickname() == null || raw.nickname().isBlank()
                ? DEFAULT_NICKNAME
                : cut(raw.nickname().strip(), NICKNAME_MAX);
        String image = raw.profileImageUrl() != null
                && (raw.profileImageUrl().startsWith("https://") || raw.profileImageUrl().startsWith("http://"))
                && raw.profileImageUrl().length() <= IMAGE_URL_MAX
                ? raw.profileImageUrl()
                : null;
        String email = raw.email() == null || raw.email().isBlank() || raw.email().length() > EMAIL_MAX
                ? null
                : raw.email().strip();
        return new OAuthProfile(raw.providerUserId(), nickname, email, image);
    }

    ClientRegistration.Builder base(String clientId, String clientSecret, String redirectUri) {
        return ClientRegistration.withRegistrationId(registrationId())
                .clientName(displayName)
                .clientId(clientId)
                .clientSecret(clientSecret == null ? "" : clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri);
    }

    public static OAuthProvider fromRegistrationId(String registrationId) {
        for (OAuthProvider provider : values()) {
            if (provider.registrationId().equals(registrationId)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 로그인 제공자입니다: " + registrationId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static String first(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
