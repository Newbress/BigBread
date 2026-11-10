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
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final AuthRateLimits limits;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public LocalAuthController(LocalAccountService accounts, UserAccountService users, AuthProperties properties,
                               AuthRateLimits limits, SessionAuthenticationStrategy sessionStrategy,
                               SecurityContextRepository securityContextRepository) {
        this.accounts = accounts;
        this.users = users;
        this.properties = properties;
        this.limits = limits;
        this.sessionStrategy = sessionStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * @param signupEnabled   회원가입을 쓸 수 있으면 true
     * @param emailRequired   가입할 때 이메일 인증이 필요하면 true (false 면 이메일 없이 바로 가입)
     * @param recoveryEnabled 메일로 아이디 찾기·비밀번호 재설정을 쓸 수 있으면 true
     */
    public record Options(boolean signupEnabled, boolean emailRequired, boolean recoveryEnabled) {
    }

    /** 화면이 가입·찾기 메뉴를 보여줄지 정하는 데 쓴다. */
    @GetMapping("/options")
    public Options options() {
        return new Options(accounts.signupAvailable(), !accounts.openSignup(), accounts.recoveryAvailable());
    }

    public record IdAvailability(boolean available) {
    }

    /** 가입 화면의 아이디 중복 확인. */
    @GetMapping("/id-available")
    public IdAvailability idAvailable(@RequestParam String loginId, HttpServletRequest httpRequest) {
        limits.checkIp("id-available", httpRequest, 60, Duration.ofMinutes(1));
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
    public ResponseEntity<?> signup(@RequestBody SignupRequest request, HttpServletRequest httpRequest,
                                    HttpServletResponse httpResponse) {
        limits.checkIp("signup", httpRequest, 10, Duration.ofHours(1));
        String loginId = LocalAuthRules.normalizeLoginId(request.loginId());
        if (accounts.openSignup()) {
            // 메일 없이 바로 가입: 이메일은 받지 않고, 만들자마자 로그인된 상태로 답한다.
            firstError(
                    LocalAuthRules.loginIdError(loginId),
                    LocalAuthRules.passwordError(request.password(), loginId),
                    LocalAuthRules.nicknameError(request.nickname()),
                    request.agreedToTerms() ? null : "이용약관과 개인정보 처리방침에 동의해야 가입할 수 있어요.");
            long userId = accounts.signupOpen(loginId, request.password(), request.nickname().strip());
            return ResponseEntity.status(HttpStatus.CREATED).body(startSession(userId, httpRequest, httpResponse));
        }
        String email = LocalAuthRules.normalizeEmail(request.email());
        firstError(
                LocalAuthRules.loginIdError(loginId),
                LocalAuthRules.emailError(email),
                LocalAuthRules.passwordError(request.password(), loginId),
                LocalAuthRules.nicknameError(request.nickname()),
                request.agreedToTerms() ? null : "개인정보 수집·이용에 동의해야 가입할 수 있어요.");

        accounts.requestSignup(loginId, email, request.password(), request.nickname().strip(), properties.baseUrl());
        // 이미 가입된 이메일이어도 똑같이 답한다.
        return ResponseEntity.accepted().body(new Message("인증 메일을 보냈어요. 메일의 링크를 24시간 안에 열어 가입을 완료해 주세요."));
    }

    public record VerifyRequest(@NotBlank String token) {
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyRequest request, HttpServletRequest httpRequest) {
        limits.checkIp("verify-email", httpRequest, 30, Duration.ofMinutes(10));
        if (accounts.verifySignup(request.token()).isEmpty()) {
            throw ApiException.badRequest(LocalAccountService.INVALID_LINK);
        }
    }

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {
    }

    @PostMapping("/login")
    public AuthController.Me login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        limits.checkIp("login", httpRequest, 30, Duration.ofMinutes(5));
        LocalAccountService.LoginResult result = accounts.authenticate(request.loginId(), request.password());
        if (result.status() == LocalAccountService.LoginStatus.LOCKED) {
            throw ApiException.tooManyRequests("로그인 시도가 너무 많아요. "
                    + LocalAccountService.LOCK_DURATION.toMinutes() + "분 뒤에 다시 시도해 주세요.");
        }
        if (result.status() != LocalAccountService.LoginStatus.SUCCESS) {
            throw ApiException.unauthorized(LOGIN_FAILED);
        }
        return startSession(result.userId(), httpRequest, httpResponse);
    }

    /** 로그인된 세션을 만든다 (세션 ID 재발급 포함). 로그인과 가입 직후 자동 로그인이 함께 쓴다. */
    private AuthController.Me startSession(long userId, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
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
