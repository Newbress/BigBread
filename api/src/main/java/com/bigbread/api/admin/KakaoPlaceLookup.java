package com.bigbread.api.admin;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 카카오 로컬 키워드 검색으로 제보된 빵집을 찾는다. 서버의 REST API 키로만 호출한다. */
public class KakaoPlaceLookup implements PlaceLookup {

    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceLookup.class);
    private static final List<String> BAKERY_CATEGORIES = List.of("제과", "베이커리");

    private final RestClient client;
    private final JsonMapper json = JsonMapper.builder().build();

    public KakaoPlaceLookup(String baseUrl, String restApiKey) {
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
    public Optional<Place> find(String name, String address) {
        try {
            String body = client.get()
                    .uri(uri -> uri.path("/v2/local/search/keyword.json")
                            .queryParam("query", name + " " + address)
                            .queryParam("size", 5)
                            .build())
                    .retrieve()
                    .body(String.class);
            for (JsonNode d : json.readTree(body).path("documents")) {
                String category = d.path("category_name").asString("");
                if (BAKERY_CATEGORIES.stream().noneMatch(category::contains)) {
                    continue;
                }
                double lng = Double.parseDouble(d.path("x").asString("NaN"));
                double lat = Double.parseDouble(d.path("y").asString("NaN"));
                if (Double.isNaN(lat) || Double.isNaN(lng)) {
                    continue;
                }
                return Optional.of(new Place(d.path("id").asString(""), d.path("place_name").asString(""),
                        d.path("road_address_name").asString(""), d.path("address_name").asString(""), lat, lng,
                        d.path("phone").asString("")));
            }
        } catch (RuntimeException e) {
            // 키가 없거나 카카오가 응답하지 않으면 운영자가 좌표를 직접 넣는다.
            log.warn("카카오 장소 검색 실패: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
