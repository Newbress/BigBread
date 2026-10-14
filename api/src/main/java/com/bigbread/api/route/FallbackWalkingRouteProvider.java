package com.bigbread.api.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 실제 경로 조회가 실패한 구간만 직선 추정({@code estimated = true})으로 대신한다. */
public class FallbackWalkingRouteProvider implements WalkingRouteProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackWalkingRouteProvider.class);

    private final WalkingRouteProvider primary;
    private final WalkingRouteProvider fallback;

    public FallbackWalkingRouteProvider(WalkingRouteProvider primary, WalkingRouteProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public WalkLeg walk(Point from, Point to) {
        try {
            return primary.walk(from, to);
        } catch (RuntimeException e) {
            log.warn("도보 경로 조회 실패, 직선 추정으로 대체합니다: {}", e.getMessage());
            return fallback.walk(from, to);
        }
    }
}
