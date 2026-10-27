package com.bigbread.collector.kakao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 넓은 영역의 장소를 빠짐없이 모은다.
 *
 * <p>카카오 키워드 검색은 한 질의에 최대 45개만 돌려준다. 그래서 영역을 격자 구역으로 나누어 검색하고,
 * 45개를 넘겨 잘린 구역은 4등분해서 다시 검색한다 (잘린 키워드만). 더 쪼갤 수 없는 크기에서도 잘리면
 * 그 구역은 일부 누락될 수 있어서 개수를 세어 알려준다.
 */
public class GridSearcher {

    private static final Logger log = LoggerFactory.getLogger(GridSearcher.class);

    /**
     * @param places         id로 중복을 제거한 결과
     * @param truncatedCells 더 쪼갤 수 없는데도 잘린 구역 수 (0이면 누락 없음)
     * @param searchedCells  검색한 구역 수
     */
    public record Result(Map<String, KakaoPlace> places, int truncatedCells, int searchedCells) {
    }

    private final PlaceSource source;
    private final double minCell;

    public GridSearcher(PlaceSource source, double minCellDegrees) {
        this.source = source;
        this.minCell = minCellDegrees;
    }

    public Result search(Rect area, double cellDegrees, List<String> keywords) {
        Map<String, KakaoPlace> places = new LinkedHashMap<>();
        int[] counters = {0, 0}; // [잘린 구역, 검색한 구역]
        for (Rect cell : area.grid(cellDegrees)) {
            searchCell(cell, keywords, places, counters);
        }
        return new Result(places, counters[0], counters[1]);
    }

    private void searchCell(Rect cell, List<String> keywords, Map<String, KakaoPlace> places, int[] counters) {
        counters[1]++;
        List<String> truncatedKeywords = new ArrayList<>();
        for (String keyword : keywords) {
            if (fetchAll(keyword, cell, places)) {
                truncatedKeywords.add(keyword);
            }
        }
        if (truncatedKeywords.isEmpty()) {
            return;
        }
        if (cell.width() / 2 >= minCell && cell.height() / 2 >= minCell) {
            for (Rect part : cell.splitInFour()) {
                searchCell(part, truncatedKeywords, places, counters);
            }
        } else {
            counters[0]++;
            log.warn("더 쪼갤 수 없는 구역에서 결과가 잘렸어요 (일부 누락 가능): {} {}", cell.toParam(), truncatedKeywords);
        }
    }

    /** 한 구역·한 키워드의 모든 페이지를 모은다. @return 결과가 45개 한계에 걸려 잘렸으면 true */
    private boolean fetchAll(String keyword, Rect cell, Map<String, KakaoPlace> places) {
        boolean truncated = false;
        for (int page = 1; page <= PlaceSource.MAX_PAGE; page++) {
            PlacePage result = source.search(keyword, cell, page);
            result.places().forEach(place -> places.putIfAbsent(place.id(), place));
            truncated |= result.truncated();
            if (result.end() || result.places().isEmpty()) {
                break;
            }
        }
        return truncated;
    }
}
