package com.bigbread.api.route;

import java.util.List;

/**
 * 직선거리로 도보 구간을 추정한다. 실제 길을 따라가지 않으므로 응답에 {@code estimated = true}로 표시된다.
 *
 * <ul>
 *   <li>보행 속도: 시속 4km (분속 약 67m) 가정</li>
 *   <li>우회 계수: 직선거리보다 실제 도보 거리가 길다는 점을 반영한 1.3배 (가정값, 실측 근거 없음)</li>
 * </ul>
 */
public class EstimatedWalkingRouteProvider implements WalkingRouteProvider {

    static final double WALK_METERS_PER_MINUTE = 4_000.0 / 60.0;
    static final double DETOUR_FACTOR = 1.3;

    @Override
    public WalkLeg walk(Point from, Point to) {
        double straight = GeoDistance.meters(from, to);
        int minutes = (int) Math.ceil(straight * DETOUR_FACTOR / WALK_METERS_PER_MINUTE);
        double rounded = Math.round(straight * 10) / 10.0;
        return new WalkLeg(rounded, minutes, List.of(from, to), true);
    }
}
