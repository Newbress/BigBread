package com.bigbread.api.auth.local;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 개발용: 메일을 보내는 대신 서버 로그에 출력한다. 링크가 로그에 남으므로 dev 프로필에서만 켠다. */
@Component
@Profile("dev")
class ConsoleAuthMailer implements AuthMailer {

    private static final Logger log = LoggerFactory.getLogger(ConsoleAuthMailer.class);

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void sendSignupVerification(String email, String nickname, String link, Duration validFor) {
        log.info("[개발용 메일] 가입 인증 -> {} ({}님) / {}시간 안에 열기: {}", email, nickname, validFor.toHours(), link);
    }

    @Override
    public void sendPasswordReset(String email, String nickname, String link, Duration validFor) {
        log.info("[개발용 메일] 비밀번호 재설정 -> {} ({}님) / {}분 안에 열기: {}", email, nickname, validFor.toMinutes(), link);
    }

    @Override
    public void sendLoginId(String email, String loginId) {
        log.info("[개발용 메일] 아이디 찾기 -> {} / 아이디: {}", email, loginId);
    }

    @Override
    public void sendPasswordChanged(String email) {
        log.info("[개발용 메일] 비밀번호가 변경되었어요 -> {}", email);
    }

    @Override
    public void sendAlreadyRegistered(String email) {
        log.info("[개발용 메일] 이미 가입된 이메일로 가입 시도 -> {}", email);
    }
}
