package com.bigbread.collector.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 카카오를 흉내 내는 로컬 서버로, 요청 형식과 응답 해석, 재시도를 확인한다. */
class KakaoLocalClientTest {

    private static final String SAMPLE = """
            {"meta":{"total_count":2,"pageable_count":2,"is_end":true},
             "documents":[
              {"id":"1001","place_name":"성심당 본점","category_name":"음식점 > 간식 > 제과,베이커리","phone":"042-220-4114",
               "address_name":"대전 중구 은행동 145","road_address_name":"대전 중구 대종로480번길 15","x":"127.427","y":"36.327",
               "place_url":"http://place.map.kakao.com/1001"},
              {"id":"1002","place_name":"좌표없는가게","category_name":"음식점","phone":"","address_name":"대전 동구","road_address_name":"","x":"","y":"",
               "place_url":""}]}
            """;

    private HttpServer server;
    private final List<String> queries = new ArrayList<>();
    private final List<String> auth = new ArrayList<>();
    private final AtomicInteger status429Left = new AtomicInteger();
    private volatile int forcedStatus = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/local/search/keyword.json", exchange -> {
            queries.add(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            int code = forcedStatus != 0 ? forcedStatus : (status429Left.getAndDecrement() > 0 ? 429 : 200);
            byte[] body = (code == 200 ? SAMPLE : "{\"errorType\":\"x\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private KakaoLocalClient client() {
        return new KakaoLocalClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-key", 1);
    }

    @Test
    void 인증_헤더와_검색_조건을_카카오_형식으로_보낸다() {
        client().search("빵집", new Rect(127.3, 36.3, 127.4, 36.4), 2);

        assertThat(auth).containsExactly("KakaoAK test-key");
        assertThat(queries.get(0)).contains("query=빵집").contains("rect=127.300000,36.300000,127.400000,36.400000")
                .contains("page=2").contains("size=15");
    }

    @Test
    void 응답을_해석하고_좌표가_없으면_NaN으로_둔다() {
        PlacePage page = client().search("빵집", new Rect(127.3, 36.3, 127.4, 36.4), 1);

        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.pageableCount()).isEqualTo(2);
        assertThat(page.end()).isTrue();
        assertThat(page.truncated()).isFalse();
        KakaoPlace first = page.places().get(0);
        assertThat(first.id()).isEqualTo("1001");
        assertThat(first.name()).isEqualTo("성심당 본점");
        assertThat(first.lng()).isEqualTo(127.427);
        assertThat(first.lat()).isEqualTo(36.327);
        assertThat(first.roadAddress()).isEqualTo("대전 중구 대종로480번길 15");
        assertThat(page.places().get(1).lat()).isNaN();
    }

    @Test
    void 일시적인_429는_다시_시도해서_성공한다() {
        status429Left.set(2);
        KakaoLocalClient client = client();

        PlacePage page = client.search("빵집", new Rect(127.3, 36.3, 127.4, 36.4), 1);

        assertThat(page.places()).hasSize(2);
        assertThat(client.calls()).isEqualTo(3);
    }

    @Test
    void 인증_거부는_다시_시도하지_않고_원인을_알려준다() {
        forcedStatus = 401;
        KakaoLocalClient client = client();

        assertThatThrownBy(() -> client.search("빵집", new Rect(127.3, 36.3, 127.4, 36.4), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REST API 키")
                .hasMessageContaining("카카오맵");
        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void 재시도해도_계속_실패하면_예외로_끝낸다() {
        forcedStatus = 500;
        KakaoLocalClient client = client();

        assertThatThrownBy(() -> client.search("빵집", new Rect(127.3, 36.3, 127.4, 36.4), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }
}
