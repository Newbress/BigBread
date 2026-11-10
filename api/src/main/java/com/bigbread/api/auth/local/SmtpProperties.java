package com.bigbread.api.auth.local;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 발송(SMTP) 설정 ({@code bigbread.mail.*}). host 를 비워 두면 메일 기능이 꺼진다(자체 회원가입 비활성).
 * Brevo, AWS SES, Gmail 앱 비밀번호 등 SMTP 로 말하는 서비스면 어디든 된다.
 *
 * @param host      SMTP 서버 주소 (예: smtp-relay.brevo.com)
 * @param port      포트. STARTTLS 는 보통 587, SSL 은 465
 * @param username  SMTP 로그인 이름 (비밀 값: .env 에만 둔다)
 * @param password  SMTP 로그인 비밀번호/키 (비밀 값: .env 에만 둔다)
 * @param security  STARTTLS(권장) | SSL | NONE(개발·시험용, 운영에서는 쓰지 않는다)
 * @param from      보내는 사람 주소. 메일 서비스에서 인증한 도메인의 주소여야 스팸함으로 가지 않는다
 * @param fromName  보내는 사람 이름
 * @param connectTimeoutMs 서버 연결 제한 시간
 * @param readTimeoutMs    응답 대기 제한 시간 (메일 서버가 느려도 요청이 오래 멈추지 않게)
 */
@ConfigurationProperties("bigbread.mail")
public record SmtpProperties(
        String host,
        Integer port,
        String username,
        String password,
        String security,
        String from,
        String fromName,
        Integer connectTimeoutMs,
        Integer readTimeoutMs) {

    public SmtpProperties {
        host = host == null ? "" : host.strip();
        port = port == null ? 587 : port;
        security = security == null || security.isBlank() ? "STARTTLS" : security.strip().toUpperCase();
        from = from == null ? "" : from.strip();
        fromName = fromName == null || fromName.isBlank() ? "대빵" : fromName.strip();
        connectTimeoutMs = connectTimeoutMs == null ? 5_000 : connectTimeoutMs;
        readTimeoutMs = readTimeoutMs == null ? 10_000 : readTimeoutMs;
    }

    /** 메일을 보낼 수 있게 설정됐는지 (host 가 있으면 켜진 것으로 본다). */
    boolean configured() {
        return !host.isEmpty();
    }
}
