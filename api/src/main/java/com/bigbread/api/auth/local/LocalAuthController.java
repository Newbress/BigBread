package com.bigbread.api.auth.local;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.auth.AuthController;
import com.bigbread.api.auth.AuthProperties;
import com.bigbread.api.auth.UserAccountService;
import com.bigbread.api.auth.UserView;
import com.bigbread.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 자체 로그인(아이디/비밀번호) API. 변경 요청은 모두 CSRF 토큰이 필요하다. */
@RestController
@RequestMapping("/api/auth")
public class LocalAuthController {

    /** 로그인 실패 메시지는 아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다. */
    static final String LOGIN_FAILED = "아이디 또는 비밀번호가 올바르지 않아요.";

    private final LocalAccountService accounts;
    private final UserAccountService users;
    private final AuthProperties properties;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public LocalAuthController(LocalAccountService accounts, UserAccountService users, AuthProperties properties,
                               SessionAuthenticationStrategy sessionStrategy,
                               SecurityContextRepository securityContextRepository) {
        this.accounts = accounts;
        this.users = users;
        this.properties = properties;
        this.sessionStrategy = sessionStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    public record IdAvailability(boolean available) {
    }

    /** 가입 화면의 아이디 중복 확인. */
    @GetMapping("/id-available")
    public IdAvailability idAvailable(@RequestParam String loginId) {
        String normalized = LocalAuthRules.normalizeLoginId(loginId);
        String error = LocalAuthRules.loginIdError(normalized);
        if (error != null) {
            throw ApiException.badRequest(error);
        }
        return new IdAvailability(!accounts.loginIdTaken(normalized));
    }

    public record SignupRequest(String loginId, String email, String password, String nickname, boolean agreedToTerms) {
    }

    public record Message(String message) {
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Message signup(@RequestBody SignupRequest request) {
        String loginId = LocalAuthRules.normalizeLoginId(request.loginId());
        String email = LocalAuthRules.normalizeEmail(request.email());
        firstError(
                LocalAuthRules.loginIdError(loginId),
                LocalAuthRules.emailError(email),
                LocalAuthRules.passwordError(request.password(), loginId),
                LocalAuthRules.nicknameError(request.nickname()),
                request.agreedToTerms() ? null : "개인정보 수집·이용에 동의해야 가입할 수 있어요.");

        accounts.requestSignup(loginId, email, request.password(), request.nickname().strip(), properties.baseUrl());
        // 이미 가입된 이메일이어도 똑같이 답한다.
        return new Message("인증 메일을 보냈어요. 메일의 링크를 24시간 안에 열어 가입을 완료해 주세요.");
    }

    public record VerifyRequest(@NotBlank String token) {
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyRequest request) {
        if (accounts.verifySignup(request.token()).isEmpty()) {
            throw ApiException.badRequest(LocalAccountService.INVALID_LINK);
        }
    }

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {
    }

    @PostMapping("/login")
    public AuthController.Me login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        long userId = accounts.authenticate(request.loginId(), request.password())
                .orElseThrow(() -> ApiException.unauthorized(LOGIN_FAILED));
        UserView user = users.find(userId).orElseThrow(() -> ApiException.unauthorized(LOGIN_FAILED));

        AppUserPrincipal principal = new AppUserPrincipal(user.id(), user.role());
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        // 세션 ID를 새로 발급한 뒤(세션 고정 공격 방지) 로그인 정보를 세션에 저장한다.
        sessionStrategy.onAuthentication(authentication, httpRequest, httpResponse);
        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        holderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        return new AuthController.Me(true, user);
    }

    private static void firstError(String... errors) {
        for (String error : errors) {
            if (error != null) {
                throw ApiException.badRequest(error);
            }
        }
    }
}
