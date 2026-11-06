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
        assertThat(check(new HashMap<>(), "prod")).hasSize(5);
    }
}
