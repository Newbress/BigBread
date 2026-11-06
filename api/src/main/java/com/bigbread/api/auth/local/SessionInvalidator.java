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

    /** 지금 쓰는 세션만 남기고 나머지 기기의 로그인을 끊는다. @return 끊은 세션 수 */
    public int invalidateOthers(long userId, String keepSessionId) {
        var found = sessions.findByPrincipalName(String.valueOf(userId));
        int count = 0;
        for (String id : found.keySet()) {
            if (!id.equals(keepSessionId)) {
                sessions.deleteById(id);
                count++;
            }
        }
        return count;
    }

    /** @return 끊은 세션 수 */
    public int invalidateAll(long userId) {
        var found = sessions.findByPrincipalName(String.valueOf(userId));
        found.keySet().forEach(sessions::deleteById);
        return found.size();
    }
}
