package com.bigbread.api.admin;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PlaceLookupConfig {

    /** REST API 키가 있으면 카카오로 찾고, 없으면 항상 못 찾은 것으로 보고 운영자가 좌표를 직접 넣게 한다. */
    @Bean
    PlaceLookup placeLookup(@Value("${bigbread.kakao.rest-api-key:}") String restApiKey,
                            @Value("${bigbread.kakao.base-url:https://dapi.kakao.com}") String baseUrl) {
        if (restApiKey.isBlank()) {
            return (name, address) -> Optional.empty();
        }
        return new KakaoPlaceLookup(baseUrl, restApiKey);
    }
}
