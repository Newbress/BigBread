package com.bigbread.api.auth.local;

import com.bigbread.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 본인확인(PASS) 방식: 인증 결과를 확인표로 바꾸고, 확인표로 비밀번호 재설정·아이디 찾기를 한다. 변경 요청은 CSRF 토큰이 필요하다. */
@RestController
@RequestMapping("/api/auth")
public class IdentityController {

    private final IdentityService identity;
    private final IdentityAccountService accounts;
    private final SessionInvalidator sessions;
    private final AuthRateLimits limits;

    public IdentityController(IdentityService identity, IdentityAccountService accounts, SessionInvalidator sessions,
                              AuthRateLimits limits) {
        this.identity = identity;
        this.accounts = accounts;
        this.sessions = sessions;
        this.limits = limits;
    }

    public record IdentityRequest(@NotBlank String purpose, @NotBlank String verificationId) {
    }

    /** 브라우저에서 본인 인증을 마친 뒤, 서버가 인증 대행사에 확인하고 1회용 확인표(10분)를 준다. 인증은 건당 비용이 드니 제한한다. */
    @PostMapping("/identity")
    public IdentityService.Ticket verify(@Valid @RequestBody IdentityRequest request, HttpServletRequest httpRequest) {
        limits.checkIp("identity", httpRequest, 10, Duration.ofHours(1));
        IdentityService.Purpose purpose;
        try {
            purpose = IdentityService.Purpose.valueOf(request.purpose().strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("알 수 없는 본인 인증 용도예요.");
        }
        return identity.issue(purpose, request.verificationId());
    }

    public record ResetRequest(@NotBlank String ticket, @NotBlank String loginId, @NotBlank String newPassword) {
    }

    @PostMapping("/password/reset-by-identity")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetRequest request, HttpServletRequest httpRequest) {
        limits.checkIp("reset-by-identity", httpRequest, 10, Duration.ofHours(1));
        String loginId = LocalAuthRules.normalizeLoginId(request.loginId());
        long userId = accounts.resetPassword(request.ticket(), loginId == null ? "" : loginId, request.newPassword());
        // 비밀번호가 바뀌었으니 다른 기기에 남아 있는 로그인도 모두 끊는다.
        sessions.invalidateAll(userId);
    }

    public record FindIdRequest(@NotBlank String ticket) {
    }

    public record FoundId(String loginId) {
    }

    @PostMapping("/find-id-by-identity")
    public FoundId findId(@Valid @RequestBody FindIdRequest request, HttpServletRequest httpRequest) {
        limits.checkIp("find-id-by-identity", httpRequest, 10, Duration.ofHours(1));
        return new FoundId(accounts.findLoginId(request.ticket()));
    }
}
