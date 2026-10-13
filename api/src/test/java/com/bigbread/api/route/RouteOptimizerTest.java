package com.bigbread.api.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RouteOptimizerTest {

    /** 위도 0.0090도 ≈ 1km. 경도는 고정해 일직선 위에 점을 놓는다. */
    private static Point north(int km) {
        return new Point(36.0 + 0.0090 * km, 127.0);
    }

    @Test
    void 정류장이_없거나_하나면_그대로다() {
        assertThat(RouteOptimizer.shortestOpenPath(List.of(), null)).isEmpty();
        assertThat(RouteOptimizer.shortestOpenPath(List.of(north(0)), north(5))).containsExactly(0);
    }

    @Test
    void 출발지가_남쪽이면_남쪽부터_북쪽으로_간다() {
        // 입력은 뒤섞어 둔다: 인덱스 0=2km, 1=0km, 2=3km, 3=1km
        List<Point> stops = List.of(north(2), north(0), north(3), north(1));
        int[] order = RouteOptimizer.shortestOpenPath(stops, north(-1));
        assertThat(order).containsExactly(1, 3, 0, 2);
    }

    @Test
    void 출발지가_북쪽이면_북쪽부터_남쪽으로_간다() {
        List<Point> stops = List.of(north(2), north(0), north(3), north(1));
        int[] order = RouteOptimizer.shortestOpenPath(stops, north(4));
        assertThat(order).containsExactly(2, 0, 3, 1);
    }

    @Test
    void 출발지가_중간이면_가까운_쪽_끝을_먼저_돌고_반대쪽으로_간다() {
        // 출발 1km 지점: 0km(1.0) → 3km(3.0)이 3km(2.0) → 0km(3.0)과 합이 같거나 짧은 쪽을 고른다.
        List<Point> stops = List.of(north(0), north(1), north(2), north(3));
        int[] order = RouteOptimizer.shortestOpenPath(stops, north(1));
        assertThat(order[0]).isEqualTo(1); // 출발지와 같은 위치의 정류장부터
        assertThat(RouteOptimizer.totalDistance(stops, order, north(1)))
                .isLessThanOrEqualTo(RouteOptimizer.totalDistance(stops, new int[] {1, 0, 2, 3}, north(1)));
    }

    @Test
    void 출발지가_없으면_어느_끝에서든_시작해_총_3km로_훑는다() {
        List<Point> stops = List.of(north(1), north(3), north(0), north(2));
        int[] order = RouteOptimizer.shortestOpenPath(stops, null);
        assertThat(RouteOptimizer.totalDistance(stops, order, null)).isCloseTo(3000, within(15.0));
        assertThat(stops.get(order[0])).isIn(north(0), north(3)); // 중간에서 시작하면 더 길다
    }

    @Test
    void 결과는_입력_인덱스의_순열이다() {
        List<Point> stops = randomPoints(new Random(7), 8);
        int[] order = RouteOptimizer.shortestOpenPath(stops, new Point(36.35, 127.38));
        assertThat(Arrays.stream(order).sorted().toArray()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void 무작위_입력에서_브루트포스_최적해와_총거리가_같다() {
        Random random = new Random(42);
        for (int round = 0; round < 300; round++) {
            int n = 2 + random.nextInt(6); // 2~7개
            List<Point> stops = randomPoints(random, n);
            Point start = random.nextBoolean() ? new Point(36.30 + random.nextDouble() * 0.1,
                    127.30 + random.nextDouble() * 0.15) : null;

            int[] order = RouteOptimizer.shortestOpenPath(stops, start);
            double actual = RouteOptimizer.totalDistance(stops, order, start);
            double expected = bruteForceMin(stops, start);

            assertThat(actual).as("round %d (n=%d, start=%s)", round, n, start).isCloseTo(expected, within(1e-6));
        }
    }

    @Test
    void 정류장이_최대_개수를_넘으면_거부한다() {
        List<Point> stops = randomPoints(new Random(1), RouteOptimizer.MAX_STOPS + 1);
        assertThatThrownBy(() -> RouteOptimizer.shortestOpenPath(stops, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 최대_개수도_계산할_수_있다() {
        List<Point> stops = randomPoints(new Random(3), RouteOptimizer.MAX_STOPS);
        int[] order = RouteOptimizer.shortestOpenPath(stops, new Point(36.35, 127.38));
        assertThat(order).hasSize(RouteOptimizer.MAX_STOPS).doesNotHaveDuplicates();
    }

    private static List<Point> randomPoints(Random random, int n) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            points.add(new Point(36.30 + random.nextDouble() * 0.1, 127.30 + random.nextDouble() * 0.15));
        }
        return points;
    }

    /** 모든 순열을 시도해 가장 짧은 총 거리를 구한다 (n ≤ 7에서만 사용). */
    private static double bruteForceMin(List<Point> stops, Point start) {
        int n = stops.size();
        double[] best = {Double.POSITIVE_INFINITY};
        permute(new int[n], new boolean[n], 0, stops, start, best);
        return best[0];
    }

    private static void permute(int[] order, boolean[] used, int depth, List<Point> stops, Point start,
                                double[] best) {
        if (depth == order.length) {
            best[0] = Math.min(best[0], RouteOptimizer.totalDistance(stops, order, start));
            return;
        }
        for (int i = 0; i < order.length; i++) {
            if (!used[i]) {
                used[i] = true;
                order[depth] = i;
                permute(order, used, depth + 1, stops, start, best);
                used[i] = false;
            }
        }
    }
}
