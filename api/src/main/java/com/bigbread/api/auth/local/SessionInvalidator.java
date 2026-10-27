package com.bigbread.api.auth.local;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

/** 사용자의 로그인 세션을 모두 끊는다 (비밀번호가 바뀌면 다른 기기의 로그인도 풀어야 한다). */
@Component
public class SessionInvalidator {

    private final FindByIndexNameSessionRepository<?> sessions;

    SessionInvalidator(FindByIndexNameSessionRepository<?> sessions) {
        this.sessions = sessions;
    }

    /** @return 끊은 세션 수 */
    public int invalidateAll(long userId) {
        var found = sessions.findByPrincipalName(String.valueOf(userId));
        found.keySet().forEach(sessions::deleteById);
        return found.size();
    }
}
