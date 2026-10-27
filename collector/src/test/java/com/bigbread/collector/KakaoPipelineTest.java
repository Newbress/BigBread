package com.bigbread.collector;

import static org.assertj.core.api.Assertions.assertThat;

import com.bigbread.collector.kakao.KakaoLocalClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 HTTP 클라이언트 → 격자 분할 → 필터 → DB 저장까지 전체 경로를, 카카오를 흉내 내는 로컬 서버로 끝까지 실행한다.
 * 가짜 서버는 카카오처럼 한 질의에 45개까지만 돌려준다.
 */
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.flyway.ignore-migration-patterns=repeatable:missing"})
@Transactional
class KakaoPipelineTest {

    record Doc(String id, String name, String category, String address, double lng, double lat) {
    }

    private HttpServer server;
    private final List<Doc> docs = new ArrayList<>();
    private int requests;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    BakeryUpserter upserter;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/local/search/keyword.json", exchange -> {
            requests++;
            Map<String, String> q = new HashMap<>();
            for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
                String[] kv = pair.split("=", 2);
                q.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
            String[] r = q.get("rect").split(",");
            double minLng = Double.parseDouble(r[0]), minLat = Double.parseDouble(r[1]);
            double maxLng = Double.parseDouble(r[2]), maxLat = Double.parseDouble(r[3]);
            int page = Integer.parseInt(q.get("page"));
            int size = Integer.parseInt(q.get("size"));

            List<Doc> inside = docs.stream()
                    .filter(d -> d.lng() >= minLng && d.lng() <= maxLng && d.lat() >= minLat && d.lat() <= maxLat)
                    .toList();
            int pageable = Math.min(inside.size(), 45);
            int from = (page - 1) * size;
            List<Doc> slice = from >= pageable ? List.of() : inside.subList(from, Math.min(from + size, pageable));
            String documents = slice.stream().map(d -> String.format(java.util.Locale.ROOT,
                    "{\"id\":\"%s\",\"place_name\":\"%s\",\"category_name\":\"%s\",\"phone\":\"042-000-%s\",\"address_name\":\"%s\","
                            + "\"road_address_name\":\"\",\"x\":\"%.6f\",\"y\":\"%.6f\",\"place_url\":\"http://place.map.kakao.com/%s\"}",
                    d.id(), d.name(), d.category(), d.id(), d.address(), d.lng(), d.lat(), d.id())).collect(Collectors.joining(","));
            byte[] body = String.format("{\"meta\":{\"total_count\":%d,\"pageable_count\":%d,\"is_end\":%b},\"documents\":[%s]}",
                    inside.size(), pageable, from + size >= pageable, documents).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void 한도를_넘는_밀집_지역을_쪼개서_모두_모아_저장한다() {
        Random random = new Random(11);
        // 작은 영역(0.04도)에 빵집 150곳이 몰려 있다 (한 번에는 45개까지만 보인다) + 걸러져야 할 것들
        for (int i = 0; i < 150; i++) {
            docs.add(new Doc("7" + String.format("%04d", i), "수집테스트빵집" + i, "음식점 > 간식 > 제과,베이커리",
                    "대전 서구 둔산동 " + i, 127.40 + random.nextDouble() * 0.04, 36.30 + random.nextDouble() * 0.04));
        }
        docs.add(new Doc("79001", "파리바게뜨 수집테스트점", "음식점 > 간식 > 제과,베이커리", "대전 서구 둔산동 1", 127.41, 36.31));
        docs.add(new Doc("79002", "수집테스트카페", "음식점 > 카페 > 커피전문점", "대전 서구 둔산동 2", 127.42, 36.32));
        docs.add(new Doc("79003", "수집테스트세종빵집", "음식점 > 간식 > 제과,베이커리", "세종특별자치시 한솔동 3", 127.43, 36.33));

        CollectorProperties props = new CollectorProperties("", false, null,
                new CollectorProperties.Area(36.30, 127.40, 36.34, 127.44, 0.04, 0.0004), null, null, null);
        KakaoBakeryJob job = new KakaoBakeryJob(props, jdbc, upserter,
                p -> new KakaoLocalClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-key", 1));

        job.run(false);

        long saved = jdbc.sql("SELECT count(*) FROM bakery WHERE kakao_place_id LIKE '7%'").query(Long.class).single();
        assertThat(saved).as("프랜차이즈·카페·대전 밖을 뺀 150곳이 모두 저장돼야 한다").isEqualTo(150);
        assertThat(requests).as("밀집 지역을 쪼개 여러 번 검색했다").isGreaterThan(3 * 4);

        job.run(false); // 다시 수집해도 늘어나지 않는다
        long again = jdbc.sql("SELECT count(*) FROM bakery WHERE kakao_place_id LIKE '7%'").query(Long.class).single();
        assertThat(again).isEqualTo(150);
    }
}
