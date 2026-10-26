package com.bigbread.api.route;

import com.bigbread.api.bakery.BakerySummary;
import java.util.List;

/**
 * 동선 계산 결과.
 *
 * @param order                적용된 방문 순서 기준
 * @param start                출발 위치 (요청에 없으면 null)
 * @param stops                방문 순서대로 정렬된 정류장
 * @param totalDistanceMeters  총 이동 거리(m)
 * @param totalDurationMinutes 총 예상 도보 시간(분)
 * @param estimated            실제 길찾기가 아니라 직선거리 기반 추정이면 true
 * @param path                 지도에 그릴 전체 경로 좌표 (출발지 → 각 정류장 순서)
 */
public record RouteResponse(
        RouteOrder order,
        Point start,
        List<Stop> stops,
        double totalDistanceMeters,
        int totalDurationMinutes,
        boolean estimated,
        List<Point> path) {

    /** 정류장. leg는 직전 지점(출발지 또는 이전 정류장)에서 여기까지의 구간이고, 출발지가 없는 첫 정류장은 null이다. */
    public record Stop(int seq, BakerySummary bakery, Leg leg) {
    }

    /**
     * 직전 지점에서 이 정류장까지의 구간.
     *
     * @param path      이 구간만의 경로 좌표 (지도에서 구간마다 색을 달리 그릴 때 쓴다)
     * @param estimated 이 구간이 실제 길찾기가 아니라 직선 추정이면 true
     */
    public record Leg(double distanceMeters, int durationMinutes, String kakaoWalkUrl, List<Point> path,
                      boolean estimated) {
    }
}
