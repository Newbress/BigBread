package com.bigbread.api.route;

/** 동선의 방문 순서 기준. 값은 course.order_mode 컬럼과 같다. */
public enum RouteOrder {
    /** 출발지(없으면 어느 빵집이든)에서 시작해 총 이동 거리가 가장 짧은 순서 */
    NEAREST,
    /** 인기 점수가 높은 빵집부터 */
    POPULARITY,
    /** 요청한 순서 그대로 */
    MANUAL
}
