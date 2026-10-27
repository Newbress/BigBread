package com.bigbread.api.bakery;

import com.bigbread.api.bakery.BakerySummary.DistrictInfo;
import java.time.OffsetDateTime;
import java.util.List;

public record BakeryDetail(
        long id,
        String name,
        DistrictInfo district,
        String roadAddress,
        String jibunAddress,
        double lat,
        double lng,
        String phone,
        Object openingHours,
        double popularityScore,
        Links links,
        List<Bread> breads,
        List<SourceLink> sourceLinks) {

    /**
     * 외부 이동 링크. 값이 없으면 null.
     * 영업시간·메뉴·리뷰처럼 우리가 가져오지 않는 정보는 이 링크로 원본(카카오맵, 네이버 지도)에서 확인하게 한다.
     */
    public record Links(String instagram, String kakaoMap, String naverMap) {
    }

    public record Bread(long id, String name, String description, Integer price, boolean signature) {
    }

    public record SourceLink(
            long id, String platform, String url, String title, String summary, String authorName,
            OffsetDateTime publishedAt) {
    }
}
