package com.bigbread.api.review.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S3 호환 저장소 흉내 서버(경로 방식 주소)로 저장·읽기·삭제를 확인한다. */
class S3PhotoStorageTest {

    private static final String KEY = "ab/" + "ab".repeat(16) + ".jpg";

    private HttpServer server;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private S3PhotoStorage storage;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath(); // /버킷/키
            try (exchange) {
                switch (exchange.getRequestMethod()) {
                    case "PUT" -> {
                        byte[] body = exchange.getRequestBody().readAllBytes();
                        String sha = exchange.getRequestHeaders().getFirst("x-amz-content-sha256");
                        objects.put(path, sha != null && sha.startsWith("STREAMING-") ? decodeChunks(body) : body);
                        exchange.getResponseHeaders().add("ETag", "\"etag\"");
                        exchange.sendResponseHeaders(200, -1);
                    }
                    case "GET" -> {
                        byte[] body = objects.get(path);
                        if (body == null) {
                            byte[] xml = "<Error><Code>NoSuchKey</Code><Message>none</Message></Error>".getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().add("Content-Type", "application/xml");
                            exchange.sendResponseHeaders(404, xml.length);
                            exchange.getResponseBody().write(xml);
                        } else {
                            exchange.sendResponseHeaders(200, body.length);
                            exchange.getResponseBody().write(body);
                        }
                    }
                    case "DELETE" -> {
                        objects.remove(path);
                        exchange.sendResponseHeaders(204, -1);
                    }
                    default -> exchange.sendResponseHeaders(405, -1);
                }
            }
        });
        server.start();
        storage = new S3PhotoStorage(new S3Properties("http://127.0.0.1:" + server.getAddress().getPort(), "auto",
                "photos-bucket", "test-access", "test-secret", true, "uploads/"));
    }

    /** 평문(http) 연결에서 SDK가 쓰는 aws-chunked 전송 형식("크기;chunk-signature=…\r\n데이터\r\n …0;…")을 풀어 원본 바이트로 되돌린다. */
    private static byte[] decodeChunks(byte[] raw) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int pos = 0;
        while (pos < raw.length) {
            int lineEnd = pos;
            while (raw[lineEnd] != '\r') {
                lineEnd++;
            }
            String header = new String(raw, pos, lineEnd - pos, StandardCharsets.US_ASCII);
            int size = Integer.parseInt(header.substring(0, header.indexOf(';')), 16);
            pos = lineEnd + 2;
            if (size == 0) {
                break;
            }
            out.write(raw, pos, size);
            pos += size + 2;
        }
        return out.toByteArray();
    }

    @AfterEach
    void stop() {
        storage.destroy();
        server.stop(0);
    }

    @Test
    void 저장하고_읽고_지운다() {
        byte[] data = {1, 2, 3, 4};
        storage.put(KEY, data);
        assertThat(objects).containsOnlyKeys("/photos-bucket/uploads/" + KEY);
        // 저장소에는 사진 바이트가 그대로 들어간다 (전송용 포장이 섞이지 않는다)
        assertThat(objects.get("/photos-bucket/uploads/" + KEY)).containsExactly(data);
        assertThat(storage.read(KEY).orElseThrow()).containsExactly(data);

        storage.delete(KEY);
        assertThat(storage.read(KEY)).isEmpty();
        storage.delete(KEY); // 없어도 오류 없이 끝난다 (멱등)
    }

    @Test
    void 없는_파일은_빈_값이다() {
        assertThat(storage.read("cd/" + "cd".repeat(16) + ".jpg")).isEmpty();
    }

    @Test
    void 형식에_맞지_않는_키는_거부한다() {
        assertThatThrownBy(() -> storage.put("../evil.jpg", new byte[1])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.read("ab/not-hex.jpg")).isInstanceOf(IllegalArgumentException.class);
        assertThat(objects).isEmpty();
    }

    @Test
    void 필수_설정이_없으면_시작하지_않는다() {
        assertThatThrownBy(() -> new S3PhotoStorage(new S3Properties(null, null, "", "k", "s", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("bucket");
        assertThatThrownBy(() -> new S3PhotoStorage(new S3Properties(null, null, "b", null, "s", null, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
