package com.bigbread.api.auth;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthProperties properties;
    private final UserAccountService accounts;

    public AuthController(AuthProperties properties, UserAccountService accounts) {
        this.properties = properties;
        this.accounts = accounts;
    }

    public record ProviderInfo(String id, String name, String loginUrl) {
    }

    /** 로그인 화면에 보여줄 제공자. 키가 설정된 제공자만 나온다. */
    @GetMapping("/auth/providers")
    public List<ProviderInfo> providers() {
        return properties.enabledProviders().stream()
                .map(p -> new ProviderInfo(p.registrationId(), p.displayName(), "/oauth2/authorization/" + p.registrationId()))
                .toList();
    }

    public record Me(boolean authenticated, UserView user) {
    }

    /** 현재 로그인 상태. 비로그인도 오류가 아니라 {@code authenticated: false}로 답한다. */
    @GetMapping("/me")
    public Me me(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return accounts.find(principal.userId()).map(user -> new Me(true, user)).orElse(new Me(false, null));
        }
        return new Me(false, null);
    }
}
