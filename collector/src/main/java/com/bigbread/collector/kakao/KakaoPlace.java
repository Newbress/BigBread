package com.bigbread.collector.kakao;

/**
 * 카카오 장소 검색 결과 한 건. x는 경도, y는 위도다.
 *
 * @param roadAddress 도로명 주소 (없으면 빈 문자열)
 */
public record KakaoPlace(
        String id,
        String name,
        String categoryName,
        String phone,
        String address,
        String roadAddress,
        double lng,
        double lat,
        String placeUrl) {
}
