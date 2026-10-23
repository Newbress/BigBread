package com.bigbread.api.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackWalkingRouteProviderTest {

    private final Point from = new Point(36.0, 127.0);
    private final Point to = new Point(36.009, 127.0);

    @Test
    void 기본_제공자가_성공하면_그_결과를_쓴다() {
        WalkLeg real = new WalkLeg(1234, 17, List.of(from, to), false);
        WalkLeg leg = new FallbackWalkingRouteProvider((a, b) -> real, new EstimatedWalkingRouteProvider())
                .walk(from, to);
        assertThat(leg).isSameAs(real);
    }

    @Test
    void 기본_제공자가_실패하면_직선_추정으로_대신한다() {
        WalkingRouteProvider failing = (a, b) -> {
            throw new IllegalStateException("boom");
        };
        WalkLeg leg = new FallbackWalkingRouteProvider(failing, new EstimatedWalkingRouteProvider()).walk(from, to);
        assertThat(leg.estimated()).isTrue();
        assertThat(leg.distanceMeters()).isGreaterThan(900);
    }

    @Test
    void 같은_지점이면_외부_호출_없이_추정이_아닌_0m다() {
        WalkingRouteProvider neverCalled = (a, b) -> {
            throw new AssertionError("호출되면 안 된다");
        };
        WalkLeg leg = new FallbackWalkingRouteProvider(neverCalled, neverCalled).walk(from, from);
        assertThat(leg.distanceMeters()).isZero();
        assertThat(leg.durationMinutes()).isZero();
        assertThat(leg.estimated()).isFalse();
    }
}
