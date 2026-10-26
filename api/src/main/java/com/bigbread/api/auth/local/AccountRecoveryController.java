package com.bigbread.api.auth.local;

import com.bigbread.api.auth.AuthProperties;
import com.bigbread.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 비밀번호 재설정과 아이디 찾기. 계정이 있는지 응답으로 알려주지 않는다. */
@RestController
@RequestMapping("/api/auth")
public class AccountRecoveryController {

    private final AccountRecoveryService recovery;
    private final SessionInvalidator sessions;
    private final AuthProperties properties;
    private final AuthRateLimits limits;

    public AccountRecoveryController(AccountRecoveryService recovery, SessionInvalidator sessions,
                                     AuthProperties properties, AuthRateLimits limits) {
        this.recovery = recovery;
        this.sessions = sessions;
        this.properties = properties;
        this.limits = limits;
    }

    public record ForgotRequest(@NotBlank String loginId) {
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LocalAuthController.Message forgotPassword(@Valid @RequestBody ForgotRequest request,
                                                      HttpServletRequest httpRequest) {
        limits.checkIp("forgot", httpRequest, 5, Duration.ofHours(1));
        String loginId = LocalAuthRules.normalizeLoginId(request.loginId());
        // 같은 계정으로 메일이 쏟아지지 않게 대상별로도 제한한다 (넘으면 조용히 보내지 않는다).
        if (loginId.length() <= 40 && limits.allowTarget("forgot", loginId, 3, Duration.ofHours(1))) {
            recovery.requestPasswordReset(loginId, properties.baseUrl());
        }
        return new LocalAuthController.Message("입력하신 아이디로 가입된 계정이 있다면, 등록된 이메일로 비밀번호 재설정 링크를 보냈어요. 링크는 "
                + AccountRecoveryService.RESET_TOKEN_TTL.toMinutes() + "분 동안 유효해요.");
    }

    public record ResetRequest(@NotBlank String token, @NotBlank String newPassword) {
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetRequest request, HttpServletRequest httpRequest) {
        limits.checkIp("reset", httpRequest, 10, Duration.ofHours(1));
        Optional<AccountRecoveryService.Reset> reset = recovery.resetPassword(request.token(), request.newPassword());
        AccountRecoveryService.Reset done =
                reset.orElseThrow(() -> ApiException.badRequest(AccountRecoveryService.INVALID_RESET_LINK));
        // 비밀번호가 바뀌었으니 다른 기기에 남아 있는 로그인도 모두 끊는다.
        sessions.invalidateAll(done.userId());
        recovery.notifyPasswordChanged(done.email());
    }

    public record FindIdRequest(@NotBlank String email) {
    }

    @PostMapping("/find-id")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LocalAuthController.Message findId(@Valid @RequestBody FindIdRequest request, HttpServletRequest httpRequest) {
        limits.checkIp("find-id", httpRequest, 5, Duration.ofHours(1));
        String email = LocalAuthRules.normalizeEmail(request.email());
        if (LocalAuthRules.emailError(email) != null) {
            throw ApiException.badRequest("이메일 형식이 올바르지 않아요.");
        }
        if (limits.allowTarget("find-id", email, 3, Duration.ofHours(1))) {
            recovery.sendLoginId(email);
        }
        return new LocalAuthController.Message("입력하신 이메일로 가입된 계정이 있다면, 아이디를 메일로 보냈어요.");
    }
}
