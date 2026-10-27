package com.bigbread.collector.kakao;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오 로컬 API 키워드 검색 ({@code GET /v2/local/search/keyword.json}).
 * 호출 사이에 잠깐 쉬고, 일시적 오류(429, 5xx)는 간격을 늘려 다시 시도한다.
 */
public class KakaoLocalClient implements PlaceSource {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClient.class);
    private static final int MAX_ATTEMPTS = 4;

    private final RestClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final long delayMs;
    private final AtomicInteger calls = new AtomicInteger();

    public KakaoLocalClient(String baseUrl, String restApiKey, long delayMs) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "KakaoAK " + restApiKey)
                .build();
        this.delayMs = delayMs;
    }

    @Override
    public int calls() {
        return calls.get();
    }

    @Override
    public PlacePage search(String query, Rect rect, int page) {
        String body = fetch(query, rect, page);
        return parse(mapper.readTree(body));
    }

    private String fetch(String query, Rect rect, int page) {
        for (int attempt = 1; ; attempt++) {
            sleep(delayMs);
            calls.incrementAndGet();
            try {
                return client.get()
                        .uri(uri -> uri.path("/v2/local/search/keyword.json")
                                .queryParam("query", query)
                                .queryParam("rect", rect.toParam())
                                .queryParam("page", page)
                                .queryParam("size", PAGE_SIZE)
                                .build())
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw new IllegalStateException("카카오 API가 인증을 거부했어요(HTTP " + status + "). REST API 키가 맞는지, "
                            + "Kakao Developers에서 [카카오맵] 사용 설정이 켜져 있는지 확인해 주세요.", e);
                }
                boolean retryable = status == 429 || status >= 500;
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    throw new IllegalStateException("카카오 장소 검색에 실패했어요(HTTP " + status + ", 검색어 '" + query + "').", e);
                }
                long wait = 1000L << (attempt - 1);
                log.warn("카카오 API HTTP {} — {}ms 뒤에 다시 시도합니다 ({}/{})", status, wait, attempt, MAX_ATTEMPTS);
                sleep(wait);
            }
        }
    }

    static PlacePage parse(JsonNode root) {
        JsonNode meta = root.path("meta");
        List<KakaoPlace> places = new ArrayList<>();
        for (JsonNode d : root.path("documents")) {
            double lng = d.path("x").asString("").isBlank() ? Double.NaN : Double.parseDouble(d.path("x").asString());
            double lat = d.path("y").asString("").isBlank() ? Double.NaN : Double.parseDouble(d.path("y").asString());
            places.add(new KakaoPlace(
                    d.path("id").asString(""),
                    d.path("place_name").asString(""),
                    d.path("category_name").asString(""),
                    d.path("phone").asString(""),
                    d.path("address_name").asString(""),
                    d.path("road_address_name").asString(""),
                    lng, lat,
                    d.path("place_url").asString("")));
        }
        return new PlacePage(places, meta.path("total_count").asInt(0), meta.path("pageable_count").asInt(0),
                meta.path("is_end").asBoolean(true));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("수집이 중단됐어요.", e);
        }
    }
}
