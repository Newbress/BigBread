package com.bigbread.collector;

import com.bigbread.collector.kakao.KakaoPlace;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 카카오 검색 결과 중 우리 서비스에 넣을 빵집인지 가린다 (대전 5개 구 안, 빵집 카테고리, 제외 이름이 아닌 것). */
public class PlaceFilter {

    public enum Decision { ACCEPT, NOT_BAKERY, OUTSIDE_AREA, EXCLUDED_NAME, BAD_DATA }

    private final List<String> categoryKeywords;
    private final List<String> excludedNames;
    /** 구 이름(예: 유성구) → district.id */
    private final Map<String, Short> districtIds;

    public PlaceFilter(CollectorProperties properties, Map<String, Short> districtIds) {
        this.categoryKeywords = properties.categoryKeywords();
        this.excludedNames = properties.excludedNames().stream().map(PlaceFilter::normalize).toList();
        this.districtIds = districtIds;
    }

    public Decision decide(KakaoPlace place) {
        if (place.id().isBlank() || place.name().isBlank() || Double.isNaN(place.lat()) || Double.isNaN(place.lng())) {
            return Decision.BAD_DATA;
        }
        if (districtOf(place) == null) {
            return Decision.OUTSIDE_AREA;
        }
        if (categoryKeywords.stream().noneMatch(place.categoryName()::contains)) {
            return Decision.NOT_BAKERY;
        }
        String name = normalize(place.name());
        if (excludedNames.stream().anyMatch(name::contains)) {
            return Decision.EXCLUDED_NAME;
        }
        return Decision.ACCEPT;
    }

    /** 주소("대전 유성구 …")에서 구 ID를 찾는다. 대전 5개 구가 아니면 null. */
    public Short districtOf(KakaoPlace place) {
        String[] tokens = place.address().trim().split("\\s+");
        if (tokens.length < 2 || !tokens[0].startsWith("대전")) {
            return null;
        }
        return districtIds.get(tokens[1]);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
