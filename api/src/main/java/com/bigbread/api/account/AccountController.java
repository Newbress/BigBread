package com.bigbread.api.account;

import com.bigbread.api.auth.AppUserPrincipal;
import com.bigbread.api.auth.local.AuthRateLimits;
import com.bigbread.api.auth.local.SessionInvalidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 계정 (로그인 필요). 비밀번호 확인이 필요한 요청은 사용자별로 횟수를 제한한다. */
@RestController
@RequestMapping("/api/my/account")
public class AccountController {

    public record PasswordChange(String currentPassword, String newPassword) {
    }

    public record Withdrawal(String password, String confirm) {
    }

    private final AccountService accounts;
    private final AuthRateLimits limits;
    private final SessionInvalidator sessions;

    public AccountController(AccountService accounts, AuthRateLimits limits, SessionInvalidator sessions) {
        this.accounts = accounts;
        this.limits = limits;
        this.sessions = sessions;
    }

    @GetMapping
    public AccountService.Info info(@AuthenticationPrincipal AppUserPrincipal me) {
        return accounts.info(me.userId());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody PasswordChange request,
                               HttpServletRequest http) {
        limits.checkUser("password-change", me.userId(), 5, Duration.ofMinutes(15));
        accounts.changePassword(me.userId(), request.currentPassword(), request.newPassword());
        HttpSession session = http.getSession(false);
        // 지금 쓰는 기기는 로그인을 유지하고, 다른 기기의 로그인은 끊는다.
        sessions.invalidateOthers(me.userId(), session == null ? null : session.getId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AppUserPrincipal me, @RequestBody Withdrawal request, HttpServletRequest http) {
        limits.checkUser("withdraw", me.userId(), 5, Duration.ofMinutes(15));
        accounts.withdraw(me.userId(), request.password(), request.confirm());
        sessions.invalidateAll(me.userId());
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
