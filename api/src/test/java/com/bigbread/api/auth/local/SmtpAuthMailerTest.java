package com.bigbread.api.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bigbread.api.common.ApiException;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SmtpAuthMailerTest {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    private SmtpAuthMailer mailer(int port) {
        return new SmtpAuthMailer(new SmtpProperties("127.0.0.1", port, "", "", "NONE", "no-reply@bread.test", "대빵", 1000, 2000));
    }

    @Test
    void 가입_인증_메일은_글자판과_HTML을_함께_보내고_닉네임을_이스케이프한다() throws Exception {
        mailer(smtp.getSmtp().getPort()).sendSignupVerification(
                "me@example.com", "<b>빵순이</b>", "https://bread.test/verify-email?token=abc", Duration.ofHours(24));

        MimeMessage[] received = smtp.getReceivedMessages();
        assertThat(received).hasSize(1);
        MimeMessage m = received[0];
        assertThat(m.getSubject()).isEqualTo("[대빵] 이메일 인증을 완료해 주세요");
        assertThat(m.getFrom()[0].toString()).contains("no-reply@bread.test");
        assertThat(m.getAllRecipients()[0].toString()).isEqualTo("me@example.com");

        String body = com.icegreen.greenmail.util.GreenMailUtil.getBody(m);
        String decoded = decode(m);
        assertThat(decoded).contains("https://bread.test/verify-email?token=abc").contains("24시간");
        assertThat(decoded).doesNotContain("<b>빵순이</b>\">").contains("&lt;b&gt;빵순이&lt;/b&gt;");
        assertThat(body).isNotBlank();
    }

    @Test
    void 비밀번호_재설정_메일은_유효_시간을_알려준다() throws Exception {
        mailer(smtp.getSmtp().getPort()).sendPasswordReset(
                "me@example.com", "빵순이", "https://bread.test/reset-password?token=xyz", Duration.ofMinutes(30));
        assertThat(decode(smtp.getReceivedMessages()[0])).contains("reset-password?token=xyz").contains("30분");
    }

    @Test
    void 메일_서버에_닿지_않으면_503으로_알린다() {
        SmtpAuthMailer down = mailer(1); // 아무도 듣지 않는 포트
        assertThatThrownBy(() -> down.sendLoginId("me@example.com", "breadlover"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("메일을 보내지 못했어요");
    }

    @Test
    void 받는_사람은_일부만_가려서_기록한다() {
        assertThat(SmtpAuthMailer.mask("abc@example.com")).isEqualTo("a**@example.com");
        assertThat(SmtpAuthMailer.mask("nonsense")).isEqualTo("***");
    }

    @Test
    void 보내는_주소가_없거나_보안_방식이_틀리면_시작_단계에서_거절한다() {
        assertThatThrownBy(() -> new SmtpAuthMailer(new SmtpProperties("h", 587, "", "", "STARTTLS", "", "대빵", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_FROM");
        assertThatThrownBy(() -> new SmtpAuthMailer(new SmtpProperties("h", 587, "", "", "TLS13", "a@b.com", "대빵", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MAIL_SECURITY");
    }

    /** 멀티파트 전체를 풀어 하나의 문자열로 (인코딩된 한글·링크 확인용). */
    private static String decode(MimeMessage m) throws Exception {
        StringBuilder sb = new StringBuilder();
        walk(m, sb);
        return sb.toString();
    }

    private static void walk(jakarta.mail.Part part, StringBuilder sb) throws Exception {
        Object c = part.getContent();
        if (c instanceof jakarta.mail.Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                walk(mp.getBodyPart(i), sb);
            }
        } else {
            sb.append(c).append('\n');
        }
    }
}
