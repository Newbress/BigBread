package com.bigbread.collector.kakao;

/** 장소 검색을 해 주는 쪽. 실제로는 카카오 로컬 API이고, 테스트에서는 가짜로 바꿔 끼운다. */
public interface PlaceSource {

    /** 카카오 키워드 검색이 한 질의에서 돌려주는 최대 개수 */
    int MAX_RESULTS = 45;
    /** 한 질의의 최대 페이지 */
    int MAX_PAGE = 45;
    int PAGE_SIZE = 15;

    PlacePage search(String query, Rect rect, int page);

    /** 호출한 API 횟수 (요약 출력용) */
    default int calls() {
        return 0;
    }
}
