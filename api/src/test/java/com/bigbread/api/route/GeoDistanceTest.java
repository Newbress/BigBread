package com.bigbread.api.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoDistanceTest {

    @Test
    void 같은_지점이면_0이다() {
        Point p = new Point(36.3283, 127.428);
        assertThat(GeoDistance.meters(p, p)).isZero();
    }

    @Test
    void 방향과_상관없이_같은_거리다() {
        Point a = new Point(36.3283, 127.428);
        Point b = new Point(36.3326, 127.4342);
        assertThat(GeoDistance.meters(a, b)).isEqualTo(GeoDistance.meters(b, a));
    }

    @Test
    void 위도_1도는_약_111km다() {
        double meters = GeoDistance.meters(new Point(36.0, 127.0), new Point(37.0, 127.0));
        assertThat(meters).isCloseTo(111_195, within(111.0)); // 0.1% 이내
    }

    @Test
    void 대전역과_은행동은_약_730m다() {
        // PostGIS(ST_Distance, 타원체) 기준 733.2m. 구면 근사라 1% 이내로 일치하면 충분하다.
        double meters = GeoDistance.meters(new Point(36.3283, 127.428), new Point(36.3326, 127.4342));
        assertThat(meters).isCloseTo(733.2, within(7.5));
    }
}
