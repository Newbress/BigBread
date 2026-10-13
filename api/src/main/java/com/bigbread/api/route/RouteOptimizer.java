package com.bigbread.api.route;

import java.util.Arrays;
import java.util.List;

/**
 * 방문 순서 계산. 정류장이 최대 {@value #MAX_STOPS}개라서 Held-Karp 동적 계획법으로 정확한 최적해를 구한다.
 * (n = 10이면 상태 수가 2^10 * 10 = 약 1만 개라 즉시 끝난다.)
 */
public final class RouteOptimizer {

    /** 한 번에 계산할 수 있는 최대 정류장 수. 메모리/시간은 O(n^2 * 2^n)이다. */
    public static final int MAX_STOPS = 10;

    private RouteOptimizer() {
    }

    /**
     * 총 이동 거리가 가장 짧은 "열린 경로"(출발지로 돌아오지 않는 경로)의 방문 순서를 구한다.
     *
     * @param stops 정류장 좌표
     * @param start 출발지. null이면 어느 정류장에서 시작해도 된다 (첫 정류장까지의 거리는 0으로 본다)
     * @return 입력 인덱스(0..n-1)의 순열. 동률이면 인덱스가 작은 쪽을 먼저 방문한다.
     */
    public static int[] shortestOpenPath(List<Point> stops, Point start) {
        int n = stops.size();
        if (n > MAX_STOPS) {
            throw new IllegalArgumentException("정류장은 최대 " + MAX_STOPS + "개까지 계산할 수 있습니다: " + n);
        }
        if (n == 0) {
            return new int[0];
        }
        if (n == 1) {
            return new int[] {0};
        }

        double[][] dist = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                dist[i][j] = GeoDistance.meters(stops.get(i), stops.get(j));
                dist[j][i] = dist[i][j];
            }
        }

        int full = (1 << n) - 1;
        double[][] best = new double[1 << n][n];
        int[][] previous = new int[1 << n][n];
        for (double[] row : best) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int[] row : previous) {
            Arrays.fill(row, -1);
        }

        for (int i = 0; i < n; i++) {
            best[1 << i][i] = start == null ? 0.0 : GeoDistance.meters(start, stops.get(i));
        }

        for (int visited = 1; visited <= full; visited++) {
            for (int last = 0; last < n; last++) {
                double current = best[visited][last];
                if ((visited & (1 << last)) == 0 || current == Double.POSITIVE_INFINITY) {
                    continue;
                }
                for (int next = 0; next < n; next++) {
                    if ((visited & (1 << next)) != 0) {
                        continue;
                    }
                    int nextVisited = visited | (1 << next);
                    double candidate = current + dist[last][next];
                    if (candidate < best[nextVisited][next]) {
                        best[nextVisited][next] = candidate;
                        previous[nextVisited][next] = last;
                    }
                }
            }
        }

        int last = 0;
        for (int i = 1; i < n; i++) {
            if (best[full][i] < best[full][last]) {
                last = i;
            }
        }

        int[] order = new int[n];
        int visited = full;
        for (int position = n - 1; position >= 0; position--) {
            order[position] = last;
            int before = previous[visited][last];
            visited &= ~(1 << last);
            last = before;
        }
        return order;
    }

    /** 순서대로 방문했을 때의 총 거리(m). start가 있으면 start → 첫 정류장 구간도 포함한다. */
    public static double totalDistance(List<Point> stops, int[] order, Point start) {
        double total = 0;
        Point from = start;
        for (int index : order) {
            Point to = stops.get(index);
            if (from != null) {
                total += GeoDistance.meters(from, to);
            }
            from = to;
        }
        return total;
    }
}
