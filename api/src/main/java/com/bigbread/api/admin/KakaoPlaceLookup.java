package com.bigbread.api.admin;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
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
        // 이름+주소로 먼저, 안 나오면 이름만(대전)으로 한 번 더 찾는다. 빵집 분류인 곳만 자동으로 고른다.
        for (String query : List.of(name + " " + address, "대전 " + name)) {
            for (Candidate c : keyword(query)) {
                if (BAKERY_CATEGORIES.stream().anyMatch(c.category()::contains)) {
                    return Optional.of(c.place());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Candidate> search(String query) {
        List<Candidate> found = keyword(query);
        if (found.isEmpty()) {
            found = address(query);
        }
        return found;
    }

    /** 카카오 키워드 검색 (장소). */
    private List<Candidate> keyword(String query) {
        List<Candidate> result = new ArrayList<>();
        try {
            String body = client.get()
                    .uri(uri -> uri.path("/v2/local/search/keyword.json").queryParam("query", query).queryParam("size", 8).build())
                    .retrieve().body(String.class);
            for (JsonNode d : json.readTree(body).path("documents")) {
                double lng = Double.parseDouble(d.path("x").asString("NaN"));
                double lat = Double.parseDouble(d.path("y").asString("NaN"));
                if (Double.isNaN(lat) || Double.isNaN(lng)) {
                    continue;
                }
                result.add(new Candidate(new Place(d.path("id").asString(""), d.path("place_name").asString(""),
                        d.path("road_address_name").asString(""), d.path("address_name").asString(""), lat, lng,
                        d.path("phone").asString("")), d.path("category_name").asString("")));
            }
        } catch (RuntimeException e) {
            // 키가 없거나 카카오가 응답하지 않으면 운영자가 다른 방법으로 위치를 정한다.
            log.warn("카카오 장소 검색 실패: {}", e.getMessage());
        }
        return result;
    }

    /** 카카오 주소 검색: 카카오에 아직 없는 새 가게도 주소만으로 위치를 잡을 수 있다 (카카오 장소 ID는 없다). */
    private List<Candidate> address(String query) {
        List<Candidate> result = new ArrayList<>();
        try {
            String body = client.get()
                    .uri(uri -> uri.path("/v2/local/search/address.json").queryParam("query", query).queryParam("size", 5).build())
                    .retrieve().body(String.class);
            for (JsonNode d : json.readTree(body).path("documents")) {
                double lng = Double.parseDouble(d.path("x").asString("NaN"));
                double lat = Double.parseDouble(d.path("y").asString("NaN"));
                if (Double.isNaN(lat) || Double.isNaN(lng)) {
                    continue;
                }
                String jibun = d.path("address_name").asString("");
                String road = d.path("road_address").path("address_name").asString("");
                result.add(new Candidate(new Place("", "", road, jibun, lat, lng, ""), "주소로 찾은 위치"));
            }
        } catch (RuntimeException e) {
            log.warn("카카오 주소 검색 실패: {}", e.getMessage());
        }
        return result;
    }
}
