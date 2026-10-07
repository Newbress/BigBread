package com.bigbread.api.bakery;

import com.bigbread.api.common.ApiException;

/**
 * 빵집 목록 검색 조건. 필드 간 규칙은 생성 시점에 검증한다.
 *
 * @param district 구 코드(DONG, JUNG, SEO, YUSEONG, DAEDEOK). null이면 전체
 * @param lat      기준 위도 (lng와 함께 지정)
 * @param lng      기준 경도 (lat와 함께 지정)
 * @param radius   반경(m). lat/lng가 있을 때만 사용 가능
 */
public record BakerySearchCondition(
        String district, Double lat, Double lng, Integer radius, BakerySort sort, int page, int size) {

    public BakerySearchCondition {
        if ((lat == null) != (lng == null)) {
            throw ApiException.badRequest("lat과 lng는 함께 지정해야 합니다.");
        }
        if (radius != null && lat == null) {
            throw ApiException.badRequest("radius를 사용하려면 lat과 lng가 필요합니다.");
        }
        if (sort == BakerySort.DISTANCE && lat == null) {
            throw ApiException.badRequest("sort=DISTANCE를 사용하려면 lat과 lng가 필요합니다.");
        }
        if (district != null) {
            district = district.trim().toUpperCase();
        }
    }

    public boolean hasLocation() {
        return lat != null;
    }

    public long offset() {
        return (long) page * size;
    }
}
