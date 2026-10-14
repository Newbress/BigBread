package com.bigbread.api.route;

/**
 * 두 지점 사이의 도보 구간을 계산한다.
 *
 * <p>{@link KakaoWalkingRouteProvider}(카카오맵 도보 경로 조회)가 기본이고, 키가 없거나 호출이 실패하면
 * {@link EstimatedWalkingRouteProvider}(직선거리 추정)가 대신한다.
 */
public interface WalkingRouteProvider {

    WalkLeg walk(Point from, Point to);
}
