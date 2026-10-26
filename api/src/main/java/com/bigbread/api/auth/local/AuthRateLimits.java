package com.bigbread.api.auth.local;

import com.bigbread.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 인증 API의 요청 횟수 제한. 한도를 넘으면 429로 거부한다.
 * 계정이 있는지 알려주지 않아야 하는 요청(비밀번호 재설정, 아이디 찾기)의 대상별 제한은
 * 거부하지 않고 조용히 메일만 보내지 않는다 ({@link #allow}).
 */
@Component
public class AuthRateLimits {

    static final String TOO_MANY = "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.";

    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(Clock.systemUTC());

    /** IP별 제한. 넘으면 429. */
    public void checkIp(String action, HttpServletRequest request, int limit, Duration window) {
        if (!limiter.tryAcquire(action + ":ip:" + clientIp(request), limit, window)) {
            throw ApiException.tooManyRequests(TOO_MANY);
        }
    }

    /** 대상(아이디, 이메일)별 제한. 넘으면 false (호출한 쪽이 조용히 처리한다). */
    boolean allowTarget(String action, String target, int limit, Duration window) {
        return limiter.tryAcquire(action + ":target:" + target.toLowerCase(Locale.ROOT), limit, window);
    }

    /** 테스트에서 제한 기록을 비운다. */
    public void reset() {
        limiter.clear();
    }

    /**
     * 클라이언트 IP. 프록시 뒤에서는 서버 설정 {@code server.forward-headers-strategy}를 켜야
     * 원래 IP가 들어온다 (켜지 않으면 프록시 주소로 모두가 한 사람처럼 집계된다).
     */
    static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
