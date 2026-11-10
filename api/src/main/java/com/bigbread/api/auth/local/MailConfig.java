package com.bigbread.api.auth.local;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * dev 가 아닐 때 쓸 메일 발송 방법을 고른다: SMTP 설정(MAIL_HOST)이 있으면 실제 발송, 없으면 회원가입을 막는다.
 * dev 에서는 {@link ConsoleAuthMailer}(로그 출력)가 대신한다.
 */
@Configuration
@EnableConfigurationProperties(SmtpProperties.class)
class MailConfig {

    @Bean
    @Profile("!dev")
    AuthMailer authMailer(SmtpProperties props) {
        return props.configured() ? new SmtpAuthMailer(props) : new UnavailableAuthMailer();
    }
}
