package com.bigbread.api.bakery;

public enum BakerySort {
    /** 인기순 (기본). 동점이면 가까운 순(위치가 있을 때) → 이름순 */
    POPULARITY,
    /** 가까운 순 (lat/lng 필수). 거리는 10m 단위로 묶고, 같으면 인기순 → 이름순 */
    DISTANCE,
    /** 이름순 (가나다). 같은 이름이면 인기순 */
    NAME
}
