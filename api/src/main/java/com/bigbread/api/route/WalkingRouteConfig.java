package com.bigbread.api.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WalkingRouteConfig {

    /** REST API 키가 있으면 카카오 실제 경로를, 없으면 직선 추정만 쓴다. */
    @Bean
    WalkingRouteProvider walkingRouteProvider(
            @Value("${bigbread.kakao.rest-api-key:}") String restApiKey,
            @Value("${bigbread.kakao.base-url:" + KakaoWalkingRouteProvider.DEFAULT_BASE_URL + "}") String baseUrl) {
        WalkingRouteProvider estimated = new EstimatedWalkingRouteProvider();
        if (restApiKey.isBlank()) {
            return estimated;
        }
        return new FallbackWalkingRouteProvider(new KakaoWalkingRouteProvider(baseUrl, restApiKey), estimated);
    }
}
