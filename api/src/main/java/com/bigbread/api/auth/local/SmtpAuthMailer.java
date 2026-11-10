package com.bigbread.api.auth.local;

import com.bigbread.api.common.ApiException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * SMTP 로 실제 메일을 보낸다 (운영용). 글자판과 HTML 을 함께 담아 보내고, 링크·토큰은 로그에 남기지 않는다.
 * 발송에 실패하면 503("잠시 후 다시")으로 알린다: 가입 요청은 롤백되어 다시 시도할 수 있고,
 * 비밀번호 재설정·아이디 찾기는 호출한 쪽이 실패를 삼켜서 계정 존재 여부가 드러나지 않는다.
 */
class SmtpAuthMailer implements AuthMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpAuthMailer.class);
    static final String SEND_FAILED = "메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요.";

    private final JavaMailSenderImpl sender;
    private final InternetAddress from;

    SmtpAuthMailer(SmtpProperties props) {
        if (props.from().isEmpty()) {
            throw new IllegalStateException("메일을 보내려면 MAIL_FROM(보내는 주소)이 필요해요.");
        }
        try {
            this.from = new InternetAddress(props.from(), props.fromName(), "UTF-8");
            this.from.validate();
        } catch (UnsupportedEncodingException | jakarta.mail.internet.AddressException e) {
            throw new IllegalStateException("MAIL_FROM 이 올바른 이메일 주소가 아니에요: " + props.from(), e);
        }
        this.sender = new JavaMailSenderImpl();
        sender.setHost(props.host());
        sender.setPort(props.port());
        sender.setDefaultEncoding("UTF-8");
        boolean auth = props.username() != null && !props.username().isBlank();
        if (auth) {
            sender.setUsername(props.username());
            sender.setPassword(props.password());
        }
        Properties mail = sender.getJavaMailProperties();
        mail.put("mail.smtp.auth", String.valueOf(auth));
        mail.put("mail.smtp.connectiontimeout", String.valueOf(props.connectTimeoutMs()));
        mail.put("mail.smtp.timeout", String.valueOf(props.readTimeoutMs()));
        mail.put("mail.smtp.writetimeout", String.valueOf(props.readTimeoutMs()));
        switch (props.security()) {
            case "STARTTLS" -> {
                mail.put("mail.smtp.starttls.enable", "true");
                mail.put("mail.smtp.starttls.required", "true"); // 암호화되지 않은 연결로 내려가지 않는다
            }
            case "SSL" -> mail.put("mail.smtp.ssl.enable", "true");
            case "NONE" -> log.warn("메일을 암호화 없이(NONE) 보내도록 설정돼 있어요. 시험용으로만 쓰세요.");
            default -> throw new IllegalStateException("MAIL_SECURITY 는 STARTTLS, SSL, NONE 중 하나예요. (현재: " + props.security() + ")");
        }
        log.info("메일 발송 설정됨: {}:{} ({}), 보내는 사람 {}", props.host(), props.port(), props.security(), props.from());
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void sendSignupVerification(String email, String nickname, String link, Duration validFor) {
        send(email, MailTemplates.signup(nickname, link, validFor));
    }

    @Override
    public void sendPasswordReset(String email, String nickname, String link, Duration validFor) {
        send(email, MailTemplates.passwordReset(nickname, link, validFor));
    }

    @Override
    public void sendLoginId(String email, String loginId) {
        send(email, MailTemplates.loginId(loginId));
    }

    @Override
    public void sendPasswordChanged(String email) {
        send(email, MailTemplates.passwordChanged());
    }

    @Override
    public void sendAlreadyRegistered(String email) {
        send(email, MailTemplates.alreadyRegistered());
    }

    private void send(String to, MailTemplates.Mail mail) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
            sender.send(message);
        } catch (MessagingException | MailException e) {
            // 받는 사람은 일부만 남기고, 링크와 내용은 남기지 않는다.
            log.warn("메일 발송 실패 ({}): {}", mask(to), e.getMessage());
            throw ApiException.serviceUnavailable(SEND_FAILED);
        }
    }

    /** abc@example.com → a**@example.com */
    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "**" + email.substring(at);
    }
}
