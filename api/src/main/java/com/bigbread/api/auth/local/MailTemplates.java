package com.bigbread.api.auth.local;

import java.time.Duration;
import org.springframework.web.util.HtmlUtils;

/** 인증 메일의 제목과 본문(글자판 + HTML). 사용자가 정한 닉네임은 HTML 에서 반드시 이스케이프한다. */
final class MailTemplates {

    record Mail(String subject, String text, String html) {
    }

    private static final String BRAND = "대빵";
    private static final String COLOR = "#b45309";

    private MailTemplates() {
    }

    static Mail signup(String nickname, String link, Duration validFor) {
        String valid = describe(validFor);
        return mail("[" + BRAND + "] 이메일 인증을 완료해 주세요",
                nickname + "님, 안녕하세요!",
                "가입을 마치려면 아래 링크를 열어 주세요. 링크는 " + valid + " 동안만 유효해요.",
                "이메일 인증하고 가입 완료", link,
                "본인이 가입을 요청하지 않았다면 이 메일을 무시해 주세요. 링크를 열지 않으면 가입되지 않아요.");
    }

    static Mail passwordReset(String nickname, String link, Duration validFor) {
        String valid = describe(validFor);
        return mail("[" + BRAND + "] 비밀번호 재설정 안내",
                nickname + "님, 비밀번호를 잊으셨나요?",
                "아래 링크에서 새 비밀번호를 정할 수 있어요. 링크는 " + valid + " 동안만 유효하고 한 번만 쓸 수 있어요.",
                "새 비밀번호 정하기", link,
                "본인이 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않아요.");
    }

    static Mail loginId(String loginId) {
        return mail("[" + BRAND + "] 아이디 안내",
                "가입하신 아이디를 알려 드려요.",
                "아이디: " + loginId,
                null, null,
                "본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
    }

    static Mail passwordChanged() {
        return mail("[" + BRAND + "] 비밀번호가 변경됐어요",
                "비밀번호가 변경됐어요.",
                "방금 계정의 비밀번호가 바뀌었어요. 다른 기기의 로그인은 모두 해제됐어요.",
                null, null,
                "본인이 바꾼 것이 아니라면 바로 '비밀번호를 잊으셨나요?'로 비밀번호를 다시 정해 주세요.");
    }

    static Mail alreadyRegistered() {
        return mail("[" + BRAND + "] 이미 가입된 이메일이에요",
                "이미 가입된 이메일이에요.",
                "이 이메일로 가입을 시도하셨지만 이미 계정이 있어요. 로그인하거나, 비밀번호가 기억나지 않으면 '비밀번호를 잊으셨나요?'를 이용해 주세요.",
                null, null,
                "본인이 시도한 것이 아니라면 이 메일을 무시해 주세요.");
    }

    private static Mail mail(String subject, String title, String body, String button, String link, String footer) {
        StringBuilder text = new StringBuilder(title).append("\n\n").append(body).append("\n");
        if (link != null) {
            text.append("\n").append(link).append("\n");
        }
        text.append("\n").append(footer).append("\n\n— ").append(BRAND).append("\n");

        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"ko\"><body style=\"margin:0;padding:24px;background:#fffaf4;"
                + "font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#2b1b10\">"
                + "<div style=\"max-width:480px;margin:0 auto;background:#fff;border:1px solid #e5d5c5;border-radius:12px;padding:24px\">"
                + "<p style=\"margin:0 0 4px;font-weight:700;color:" + COLOR + "\">" + BRAND + " 🥖</p>"
                + "<h1 style=\"margin:0 0 16px;font-size:20px\">").append(esc(title)).append("</h1>")
                .append("<p style=\"margin:0 0 16px;line-height:1.6\">").append(esc(body)).append("</p>");
        if (link != null) {
            html.append("<p style=\"margin:0 0 16px\"><a href=\"").append(esc(link)).append("\" style=\"display:inline-block;padding:12px 20px;"
                            + "background:").append(COLOR).append(";color:#fff;text-decoration:none;border-radius:8px;font-weight:700\">")
                    .append(esc(button)).append("</a></p>")
                    .append("<p style=\"margin:0 0 16px;font-size:12px;color:#6b7280;word-break:break-all\">버튼이 안 눌리면 이 주소를 복사해 열어 주세요:<br>")
                    .append(esc(link)).append("</p>");
        }
        html.append("<p style=\"margin:0;font-size:13px;color:#6b7280;line-height:1.5\">").append(esc(footer)).append("</p></div></body></html>");
        return new Mail(subject, text.toString(), html.toString());
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value);
    }

    private static String describe(Duration d) {
        return d.toHours() >= 1 ? d.toHours() + "시간" : d.toMinutes() + "분";
    }
}
