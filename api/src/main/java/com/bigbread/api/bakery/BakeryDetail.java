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

    /** 외부 이동 링크. 값이 없으면 null. */
    public record Links(String instagram, String kakaoMap) {
    }

    public record Bread(long id, String name, String description, Integer price, boolean signature) {
    }

    public record SourceLink(
            long id, String platform, String url, String title, String summary, String authorName,
            OffsetDateTime publishedAt) {
    }
}
