package com.bigbread.api.auth.local;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfig {

    @Bean
    IdentityVerifier identityVerifier(IdentityProperties properties) {
        return switch (properties.provider()) {
            case "fake" -> new FakeIdentityVerifier();
            default -> new UnavailableIdentityVerifier();
        };
    }
}
