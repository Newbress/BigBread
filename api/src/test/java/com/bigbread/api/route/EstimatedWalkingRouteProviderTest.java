package com.bigbread.api.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class EstimatedWalkingRouteProviderTest {

    private final EstimatedWalkingRouteProvider provider = new EstimatedWalkingRouteProvider();

    @Test
    void 약_1km_구간은_추정값으로_표시되고_경로는_두_점이다() {
        Point from = new Point(36.0, 127.0);
        Point to = new Point(36.0090, 127.0); // 북쪽 약 1km
        WalkLeg leg = provider.walk(from, to);

        assertThat(leg.estimated()).isTrue();
        assertThat(leg.distanceMeters()).isCloseTo(1000, within(5.0));
        assertThat(leg.path()).containsExactly(from, to);
    }

    @Test
    void 소요_시간은_우회계수를_적용해_올림한다() {
        // 약 1000m * 1.3 / (4000/60 m/분) = 19.5분 → 20분
        WalkLeg leg = provider.walk(new Point(36.0, 127.0), new Point(36.0090, 127.0));
        assertThat(leg.durationMinutes()).isEqualTo(20);
    }

    @Test
    void 같은_위치면_0분이다() {
        Point p = new Point(36.0, 127.0);
        WalkLeg leg = provider.walk(p, p);
        assertThat(leg.distanceMeters()).isZero();
        assertThat(leg.durationMinutes()).isZero();
    }
}
