package com.bigbread.api.route;

import com.bigbread.api.bakery.BakeryQueryService;
import com.bigbread.api.bakery.BakerySummary;
import com.bigbread.api.common.ApiException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 선택한 빵집들의 방문 순서를 정하고 구간별 도보 정보를 붙인다. */
@Service
public class RouteService {

    private static final String START_NAME = "출발지";

    private final BakeryQueryService bakeries;
    private final WalkingRouteProvider walking;

    public RouteService(BakeryQueryService bakeries, WalkingRouteProvider walking) {
        this.bakeries = bakeries;
        this.walking = walking;
    }

    public RouteResponse plan(RouteRequest request) {
        List<Long> ids = request.bakeryIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw ApiException.badRequest("bakeryIds에 중복된 빵집이 있습니다.");
        }

        Map<Long, BakerySummary> found = bakeries.findSummariesByIds(ids).stream()
                .collect(Collectors.toMap(BakerySummary::id, Function.identity()));
        List<Long> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("존재하지 않거나 볼 수 없는 빵집입니다: " + missing);
        }

        List<BakerySummary> requested = ids.stream().map(found::get).toList();
        Point start = request.startPoint();
        RouteOrder order = request.orderOrDefault();
        return build(order, start, arrange(order, requested, start));
    }

    private List<BakerySummary> arrange(RouteOrder order, List<BakerySummary> requested, Point start) {
        return switch (order) {
            case MANUAL -> requested;
            case POPULARITY -> requested.stream()
                    .sorted(Comparator.comparingDouble(BakerySummary::popularityScore).reversed()
                            .thenComparingLong(BakerySummary::id))
                    .toList();
            case NEAREST -> {
                List<Point> points = requested.stream().map(b -> new Point(b.lat(), b.lng())).toList();
                int[] indexes = RouteOptimizer.shortestOpenPath(points, start);
                List<BakerySummary> ordered = new ArrayList<>(indexes.length);
                for (int index : indexes) {
                    ordered.add(requested.get(index));
                }
                yield ordered;
            }
        };
    }

    private RouteResponse build(RouteOrder order, Point start, List<BakerySummary> ordered) {
        List<RouteResponse.Stop> stops = new ArrayList<>(ordered.size());
        List<Point> path = new ArrayList<>();
        double totalMeters = 0;
        int totalMinutes = 0;
        boolean estimated = false;

        Point previous = start;
        String previousName = START_NAME;
        if (start != null) {
            path.add(start);
        }

        for (int i = 0; i < ordered.size(); i++) {
            BakerySummary bakery = ordered.get(i);
            Point here = new Point(bakery.lat(), bakery.lng());

            RouteResponse.Leg leg = null;
            if (previous != null) {
                WalkLeg walk = walking.walk(previous, here);
                leg = new RouteResponse.Leg(walk.distanceMeters(), walk.durationMinutes(),
                        KakaoMapLinks.walk(previousName, previous, bakery.name(), here), walk.path(),
                        walk.estimated());
                totalMeters += walk.distanceMeters();
                totalMinutes += walk.durationMinutes();
                estimated |= walk.estimated();
                appendPath(path, walk.path());
            } else {
                path.add(here);
            }

            stops.add(new RouteResponse.Stop(i + 1, bakery, leg));
            previous = here;
            previousName = bakery.name();
        }

        return new RouteResponse(order, start, stops, Math.round(totalMeters * 10) / 10.0, totalMinutes,
                estimated, path);
    }

    /** 구간 경로를 이어 붙인다. 이전 구간의 끝점과 같은 시작점은 중복이라 건너뛴다. */
    private static void appendPath(List<Point> path, List<Point> segment) {
        boolean skipFirst = !path.isEmpty() && !segment.isEmpty() && path.get(path.size() - 1).equals(segment.get(0));
        path.addAll(skipFirst ? segment.subList(1, segment.size()) : segment);
    }
}
