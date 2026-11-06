package com.bigbread.api.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * 운영(prod) 프로필로 시작할 때 위험한 설정이 하나라도 있으면 서버를 시작하지 않는다.
 * 개발용 기본값(약한 DB 비밀번호, http 주소, 꺼진 보안 쿠키 등)이 운영에 그대로 나가는 사고를 막는다.
 */
@Configuration
@Profile("prod")
class ProductionChecks {

    private static final Set<String> WEAK_DB_PASSWORDS = Set.of("", "bigbread", "password", "postgres", "change-me", "changeme");

    /**
     * 다른 빈(DB, 사진 저장소 등)이 만들어지기 전에 검사한다. 그래야 설정이 잘못됐을 때 DB 연결 오류 같은 엉뚱한 메시지보다
     * "무엇을 고쳐야 하는지"가 먼저, 한꺼번에 나온다.
     */
    @Bean
    static BeanFactoryPostProcessor productionSettingsCheck(Environment env) {
        return beanFactory -> {
            List<String> problems = problems(env::getProperty, Set.of(env.getActiveProfiles()));
            if (!problems.isEmpty()) {
                throw new IllegalStateException("운영 설정이 안전하지 않아 시작하지 않아요:\n - " + String.join("\n - ", problems));
            }
        };
    }

    /** 순수 함수: 설정 값 조회와 활성 프로필만으로 문제 목록을 만든다 (테스트하기 쉽게 분리). */
    static List<String> problems(Function<String, String> property, Set<String> activeProfiles) {
        List<String> problems = new ArrayList<>();
        if (activeProfiles.contains("dev")) {
            problems.add("dev 프로필이 같이 켜져 있어요. 샘플 데이터와 콘솔 메일이 운영에 들어가요. (SPRING_PROFILES_ACTIVE=prod 만 쓰세요)");
        }
        String baseUrl = property.apply("bigbread.auth.base-url");
        if (baseUrl == null || !baseUrl.startsWith("https://")) {
            problems.add("APP_BASE_URL 이 https:// 로 시작해야 해요. (현재: " + baseUrl + ")");
        }
        if (!"true".equalsIgnoreCase(property.apply("server.servlet.session.cookie.secure"))) {
            problems.add("SESSION_COOKIE_SECURE=true 여야 해요. (로그인 쿠키가 http 로 새어 나가지 않게)");
        }
        String forwarded = property.apply("server.forward-headers-strategy");
        if (forwarded == null || forwarded.isBlank() || "none".equalsIgnoreCase(forwarded)) {
            problems.add("FORWARD_HEADERS_STRATEGY=framework 여야 해요. (프록시 뒤에서 접속자 IP를 제대로 보려면)");
        }
        String dbPassword = property.apply("spring.datasource.password");
        if (dbPassword == null || WEAK_DB_PASSWORDS.contains(dbPassword.toLowerCase())) {
            problems.add("DB_PASSWORD 가 비어 있거나 기본값이에요. 길고 무작위인 비밀번호로 바꿔 주세요.");
        }
        String dbUrl = property.apply("spring.datasource.url");
        if (dbUrl == null || dbUrl.contains("localhost")) {
            problems.add("DB_URL 이 localhost 를 가리켜요. (Compose 에서는 jdbc:postgresql://db:5432/… 여야 해요)");
        }
        // 사진 저장소: s3(기본)이면 접속 정보가 있어야 한다. 없으면 긴 오류 대신 여기서 바로 알려 준다.
        String storage = property.apply("bigbread.photos.storage");
        if (storage == null || storage.isBlank() || "s3".equalsIgnoreCase(storage)) {
            List<String> missing = new ArrayList<>();
            if (isBlank(property.apply("bigbread.photos.s3.bucket"))) {
                missing.add("PHOTO_S3_BUCKET");
            }
            if (isBlank(property.apply("bigbread.photos.s3.access-key"))) {
                missing.add("PHOTO_S3_ACCESS_KEY");
            }
            if (isBlank(property.apply("bigbread.photos.s3.secret-key"))) {
                missing.add("PHOTO_S3_SECRET_KEY");
            }
            if (!missing.isEmpty()) {
                problems.add("사진 저장소가 s3 인데 " + String.join(", ", missing)
                        + " 가 비어 있어요. 값을 채우거나, 서버 디스크에 저장하려면 PHOTO_STORAGE=local 로 바꿔 주세요.");
            }
        } else if (!"local".equalsIgnoreCase(storage)) {
            problems.add("PHOTO_STORAGE 는 s3 또는 local 이어야 해요. (현재: " + storage + ")");
        }
        // 메일: host 를 비워 두는 건 허용(자체 회원가입만 꺼짐). 켰다면 보내는 주소가 있어야 하고 암호화되어야 한다.
        if (!isBlank(property.apply("bigbread.mail.host"))) {
            if (isBlank(property.apply("bigbread.mail.from"))) {
                problems.add("MAIL_HOST 가 있는데 MAIL_FROM(보내는 주소)이 비어 있어요.");
            }
            if ("none".equalsIgnoreCase(property.apply("bigbread.mail.security"))) {
                problems.add("MAIL_SECURITY=NONE 은 운영에서 쓸 수 없어요. (STARTTLS 또는 SSL)");
            }
        }
        return problems;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
