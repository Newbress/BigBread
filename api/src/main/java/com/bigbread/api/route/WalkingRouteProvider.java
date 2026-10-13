package com.bigbread.api.route;

/**
 * 두 지점 사이의 도보 구간을 계산한다.
 *
 * <p>지금은 {@link EstimatedWalkingRouteProvider}(직선거리 추정)만 있다. 카카오 모빌리티 도보 길찾기(Directions)
 * 권한을 받으면 이 인터페이스의 구현체를 추가해 실제 경로선과 소요 시간으로 교체한다.
 */
public interface WalkingRouteProvider {

    WalkLeg walk(Point from, Point to);
}
