package com.bigbread.collector.kakao;

import java.util.List;

/**
 * 장소 검색 한 페이지.
 *
 * @param totalCount    조건에 맞는 전체 개수
 * @param pageableCount 가져올 수 있는 개수 (카카오는 최대 45개까지만 보여준다)
 */
public record PlacePage(List<KakaoPlace> places, int totalCount, int pageableCount, boolean end) {

    /** 한 번에 가져올 수 있는 한계(45)에 걸려 일부가 잘렸는가. */
    public boolean truncated() {
        return totalCount > pageableCount || pageableCount >= PlaceSource.MAX_RESULTS;
    }
}
