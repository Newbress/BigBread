package com.bigbread.api.route;

import java.util.List;

/**
 * 두 지점 사이의 도보 구간.
 *
 * @param distanceMeters  거리(m)
 * @param durationMinutes 예상 소요 시간(분)
 * @param path            지도에 그릴 경로 좌표 (시작점과 끝점을 포함)
 * @param estimated       실제 길찾기 결과가 아니라 직선거리 기반 추정이면 true
 */
public record WalkLeg(double distanceMeters, int durationMinutes, List<Point> path, boolean estimated) {
}
