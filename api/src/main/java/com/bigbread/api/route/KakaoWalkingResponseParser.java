package com.bigbread.api.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오맵 도보 경로 조회({@code GET /v2/routing/walk}) 응답을 {@link WalkLeg}로 바꾼다.
 *
 * <p>{@code status}가 {@code OK}가 아니거나 구조가 예상과 다르면 빈 값을 돌려준다.
 * 좌표는 {@code [x(경도), y(위도)]} 순서다.
 */
final class KakaoWalkingResponseParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private KakaoWalkingResponseParser() {
    }

    static Optional<WalkLeg> parse(String json) {
        JsonNode root = MAPPER.readTree(json);
        if (!"OK".equals(root.path("status").asString())) {
            return Optional.empty();
        }
        JsonNode properties = root.path("route").path("properties");
        if (!properties.has("totalDistance") || !properties.has("totalTime")) {
            return Optional.empty();
        }

        List<Point> path = new ArrayList<>();
        for (JsonNode leg : root.path("route").path("legs")) {
            for (JsonNode step : leg.path("steps")) {
                for (JsonNode xy : step.path("path").path("points")) {
                    if (xy.size() < 2) {
                        continue;
                    }
                    Point point = new Point(xy.get(1).asDouble(), xy.get(0).asDouble());
                    if (path.isEmpty() || !path.get(path.size() - 1).equals(point)) {
                        path.add(point);
                    }
                }
            }
        }
        if (path.size() < 2) {
            return Optional.empty();
        }

        int minutes = (int) Math.ceil(properties.get("totalTime").asInt() / 60.0);
        return Optional.of(new WalkLeg(properties.get("totalDistance").asInt(), minutes, path, false));
    }
}
