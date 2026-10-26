package com.bigbread.api.auth.local;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * dev가 아닌 환경의 기본값: 메일을 보낼 수단이 아직 없으니 메일이 필요한 기능(회원가입)을 막는다.
 * 인증 링크가 로그에 남거나, 메일 없이 가입되는 일이 없도록 일부러 실패시킨다.
 */
@Component
@Profile("!dev")
class UnavailableAuthMailer implements AuthMailer {

    private static final Logger log = LoggerFactory.getLogger(UnavailableAuthMailer.class);

    UnavailableAuthMailer() {
        log.warn("메일 발송 수단이 설정되지 않아 자체 회원가입이 비활성화됩니다. (SMTP 구현체를 추가하면 켜집니다)");
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public void sendSignupVerification(String email, String nickname, String link, Duration validFor) {
        throw new IllegalStateException("메일 발송 수단이 설정되지 않았습니다.");
    }

    @Override
    public void sendAlreadyRegistered(String email) {
        throw new IllegalStateException("메일 발송 수단이 설정되지 않았습니다.");
    }
}
