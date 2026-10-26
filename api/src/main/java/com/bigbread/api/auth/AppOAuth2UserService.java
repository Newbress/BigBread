package com.bigbread.api.auth;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** 제공자에서 사용자 정보를 받아 서비스 사용자로 연결하고, 세션에 저장할 {@link AppUserPrincipal}을 만든다. */
public class AppOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final UserAccountService accounts;

    public AppOAuth2UserService(UserAccountService accounts) {
        this(new DefaultOAuth2UserService(), accounts);
    }

    AppOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate, UserAccountService accounts) {
        this.delegate = delegate;
        this.accounts = accounts;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuthProvider provider = OAuthProvider.fromRegistrationId(request.getClientRegistration().getRegistrationId());
        OAuth2User user = delegate.loadUser(request);

        OAuthProfile profile = OAuthProvider.sanitize(provider.extract(user.getAttributes()));
        if (profile.providerUserId() == null || profile.providerUserId().isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"),
                    provider.displayName() + " 사용자 정보에 ID가 없습니다.");
        }

        long userId = accounts.loginOrRegister(provider, profile);
        String role = accounts.find(userId).map(UserView::role).orElse("USER");
        return new AppUserPrincipal(userId, role);
    }
}
