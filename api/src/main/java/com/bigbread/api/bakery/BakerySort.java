package com.bigbread.api.bakery;

public enum BakerySort {
    /** 인기순 (기본) */
    POPULARITY,
    /** 가까운 순 (lat/lng 필수) */
    DISTANCE,
    /** 이름순 */
    NAME
}
