package com.bigbread.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductionChecksTest {

    private static Map<String, String> good() {
        Map<String, String> p = new HashMap<>();
        p.put("bigbread.auth.base-url", "https://bread.example.com");
        p.put("server.servlet.session.cookie.secure", "true");
        p.put("server.forward-headers-strategy", "framework");
        p.put("spring.datasource.password", "a-long-random-secret-9f3k2");
        p.put("spring.datasource.url", "jdbc:postgresql://db:5432/bigbread");
        p.put("bigbread.photos.storage", "local");
        return p;
    }

    private static List<String> check(Map<String, String> props, String... profiles) {
        return ProductionChecks.problems(props::get, Set.of(profiles));
    }

    @Test
    void 안전한_설정이면_문제가_없다() {
        assertThat(check(good(), "prod")).isEmpty();
    }

    @Test
    void 개발용_기본값은_하나씩_걸러낸다() {
        Map<String, String> p = good();
        p.put("bigbread.auth.base-url", "http://localhost:5173");
        p.put("server.servlet.session.cookie.secure", "false");
        p.put("server.forward-headers-strategy", "none");
        p.put("spring.datasource.password", "bigbread");
        p.put("spring.datasource.url", "jdbc:postgresql://localhost:5432/bigbread");
        List<String> problems = check(p, "prod", "dev");
        assertThat(problems).hasSize(6);
        assertThat(String.join("\n", problems)).contains("dev 프로필").contains("APP_BASE_URL").contains("SESSION_COOKIE_SECURE")
                .contains("FORWARD_HEADERS_STRATEGY").contains("DB_PASSWORD").contains("DB_URL");
    }

    @Test
    void 값이_아예_없어도_문제로_본다() {
        // 값이 없으면 사진 저장소는 기본값(s3)으로 보고 함께 걸린다.
        assertThat(check(new HashMap<>(), "prod")).hasSize(6);
    }

    @Test
    void 사진_저장소가_s3인데_접속_정보가_없으면_이유와_해결책을_알려준다() {
        Map<String, String> p = good();
        p.put("bigbread.photos.storage", "s3");
        List<String> problems = check(p, "prod");
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("PHOTO_S3_BUCKET").contains("PHOTO_S3_ACCESS_KEY").contains("PHOTO_S3_SECRET_KEY")
                .contains("PHOTO_STORAGE=local");

        p.put("bigbread.photos.s3.bucket", "photos");
        p.put("bigbread.photos.s3.access-key", "ak");
        p.put("bigbread.photos.s3.secret-key", "sk");
        assertThat(check(p, "prod")).isEmpty();
    }

    @Test
    void 사진_저장소_종류가_이상하면_거부한다() {
        Map<String, String> p = good();
        p.put("bigbread.photos.storage", "ftp");
        assertThat(check(p, "prod")).hasSize(1).first().asString().contains("s3 또는 local");
    }

    @Test
    void 본인확인은_가상_제공자를_막고_pass_방식이면_제공자와_비밀값을_요구한다() {
        Map<String, String> p = good();
        p.put("bigbread.identity.provider", "fake");
        assertThat(String.join("\n", check(p, "prod"))).contains("fake");

        p = good();
        p.put("bigbread.auth.signup-mode", "pass");
        assertThat(String.join("\n", check(p, "prod"))).contains("IDENTITY_PROVIDER").contains("PASS_DI_PEPPER");

        p.put("bigbread.identity.provider", "someprovider");
        p.put("bigbread.identity.di-pepper", "x".repeat(32));
        assertThat(check(p, "prod")).isEmpty();
    }

    @Test
    void 메일을_켰다면_보내는_주소가_있어야_하고_암호화되어야_한다() {
        Map<String, String> p = good();
        p.put("bigbread.mail.host", "smtp.example.com");
        p.put("bigbread.mail.security", "NONE");
        assertThat(String.join("\n", check(p, "prod"))).contains("MAIL_FROM").contains("MAIL_SECURITY");

        p.put("bigbread.mail.from", "no-reply@bread.example.com");
        p.put("bigbread.mail.security", "STARTTLS");
        assertThat(check(p, "prod")).isEmpty();
    }
}
