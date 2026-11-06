package com.bigbread.api.auth.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bigbread.api.auth.CookieSession;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 SMTP(GreenMail)로 메일이 나가는 구성에서 가입 → 메일 → 인증 → 로그인이 이어지는지. */
@SpringBootTest(properties = {"bigbread.auth.base-url=http://localhost:5173", "bigbread.kakao.rest-api-key="})
@AutoConfigureMockMvc
@Transactional
class SmtpSignupFlowTest {

    private static final GreenMail SMTP;
    private static final int PORT;

    static {
        try (ServerSocket s = new ServerSocket(0)) {
            PORT = s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SMTP = new GreenMail(new ServerSetup(PORT, "127.0.0.1", "smtp"));
        SMTP.start();
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry r) {
        r.add("bigbread.mail.host", () -> "127.0.0.1");
        r.add("bigbread.mail.port", () -> PORT);
        r.add("bigbread.mail.security", () -> "NONE");
        r.add("bigbread.mail.from", () -> "no-reply@bread.test");
    }

    @AfterAll
    static void stop() {
        SMTP.stop();
    }

    @Autowired
    MockMvc mvc;

    @Test
    void 메일_설정이_있으면_가입이_열리고_받은_링크로_인증하면_로그인된다() throws Exception {
        mvc.perform(get("/api/auth/options")).andExpect(status().isOk()).andExpect(jsonPath("$.signupEnabled").value(true));
        Cookie xsrf = mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");

        mvc.perform(post("/api/auth/signup").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"smtpuser1\",\"email\":\"smtp1@example.com\",\"password\":\"BreadLover7!x\","
                                + "\"nickname\":\"메일손님\",\"agreedToTerms\":true}"))
                .andExpect(status().isAccepted());

        assertThat(SMTP.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage mail = SMTP.getReceivedMessages()[0];
        assertThat(mail.getAllRecipients()[0].toString()).isEqualTo("smtp1@example.com");
        Matcher m = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)").matcher(text(mail));
        assertThat(m.find()).as("메일 본문에 인증 링크").isTrue();

        mvc.perform(post("/api/auth/verify-email").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + m.group(1) + "\"}"))
                .andExpect(status().isNoContent());

        CookieSession browser = new CookieSession(xsrf);
        browser.perform(mvc, browser.withCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"smtpuser1\",\"password\":\"BreadLover7!x\"}"))
                .andExpect(status().isOk());
    }

    private static String text(jakarta.mail.Part part) throws Exception {
        Object c = part.getContent();
        if (c instanceof jakarta.mail.Multipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                sb.append(text(mp.getBodyPart(i)));
            }
            return sb.toString();
        }
        return String.valueOf(c);
    }
}
