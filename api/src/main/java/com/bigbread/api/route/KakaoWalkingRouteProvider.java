package com.bigbread.api.route;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 카카오맵 도보 경로 조회 API로 실제 길을 따라가는 구간을 구한다.
 * 호출 한 번이 구간 하나(출발 → 도착)다. 인증은 서버의 REST API 키로만 하며 브라우저에는 노출되지 않는다.
 */
public class KakaoWalkingRouteProvider implements WalkingRouteProvider {

    static final String DEFAULT_BASE_URL = "https://dapi.kakao.com";

    private final RestClient client;

    public KakaoWalkingRouteProvider(String baseUrl, String restApiKey) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "KakaoAK " + restApiKey)
                .build();
    }

    @Override
    public WalkLeg walk(Point from, Point to) {
        String body = client.get()
                .uri(uri -> uri.path("/v2/routing/walk")
                        .queryParam("start_x", coord(from.lng()))
                        .queryParam("start_y", coord(from.lat()))
                        .queryParam("end_x", coord(to.lng()))
                        .queryParam("end_y", coord(to.lat()))
                        .build())
                .retrieve()
                .body(String.class);
        return KakaoWalkingResponseParser.parse(body)
                .orElseThrow(() -> new IllegalStateException("도보 경로를 찾지 못했습니다."));
    }

    private static String coord(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }
}
