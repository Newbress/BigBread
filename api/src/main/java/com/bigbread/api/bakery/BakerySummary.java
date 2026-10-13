package com.bigbread.api.bakery;

import java.util.List;

/** 목록/지도 마커용 요약. distanceMeters는 기준 위치가 주어졌을 때만 채워진다. */
public record BakerySummary(
        long id,
        String name,
        DistrictInfo district,
        String roadAddress,
        double lat,
        double lng,
        double popularityScore,
        List<String> signatureBreads,
        Double distanceMeters) {

    public record DistrictInfo(short id, String code, String name) {
    }
}
