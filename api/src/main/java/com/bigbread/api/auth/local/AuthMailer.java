package com.bigbread.api.auth.local;

import java.time.Duration;

/**
 * 인증 관련 메일 발송. 구현체를 바꿔 끼울 수 있게 인터페이스로 둔다.
 * 개발: 콘솔 출력({@link ConsoleAuthMailer}), 그 외: 발송 수단이 없으므로 회원가입을 막는다({@link UnavailableAuthMailer}).
 * 운영 SMTP 연동은 이 인터페이스의 구현체를 추가하면 된다.
 */
public interface AuthMailer {

    /** 실제로 메일을 보낼 수 있는 구현이면 true. false면 메일이 필요한 기능(회원가입 등)을 막는다. */
    boolean available();

    /** 가입 인증 메일: 링크를 누르면 가입이 완료된다. */
    void sendSignupVerification(String email, String nickname, String link, Duration validFor);

    /** 이미 가입된 이메일로 가입을 시도했을 때, 그 이메일 주인에게만 알린다. (화면 응답으로는 존재 여부를 드러내지 않는다) */
    void sendAlreadyRegistered(String email);
}
