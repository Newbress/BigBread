package com.bigbread.api.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * 소셜 로그인 설정. 클라이언트 ID가 비어 있는 제공자는 꺼진 것으로 본다.
 *
 * @param baseUrl 사용자가 접속하는 주소 (개발: 프론트 개발 서버 http://localhost:5173).
 *                제공자 콘솔에 등록하는 리다이렉트 URI는 {@code {baseUrl}/login/oauth2/code/{kakao|naver|google}}이다.
 */
@ConfigurationProperties("bigbread.auth")
public record AuthProperties(String baseUrl, Client kakao, Client naver, Client google) {

    public record Client(String clientId, String clientSecret) {
        boolean enabled() {
            return clientId != null && !clientId.isBlank();
        }
    }

    public AuthProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:5173" : stripTrailingSlash(baseUrl);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private Client client(OAuthProvider provider) {
        return switch (provider) {
            case KAKAO -> kakao;
            case NAVER -> naver;
            case GOOGLE -> google;
        };
    }

    /** 키가 설정된 제공자들 (선언 순서). */
    public List<OAuthProvider> enabledProviders() {
        List<OAuthProvider> enabled = new ArrayList<>();
        for (OAuthProvider provider : OAuthProvider.values()) {
            Client client = client(provider);
            if (client != null && client.enabled()) {
                enabled.add(provider);
            }
        }
        return enabled;
    }

    public List<ClientRegistration> registrations() {
        return enabledProviders().stream()
                .map(provider -> {
                    Client client = client(provider);
                    return provider.toRegistration(client.clientId(), client.clientSecret(), baseUrl);
                })
                .toList();
    }
}
