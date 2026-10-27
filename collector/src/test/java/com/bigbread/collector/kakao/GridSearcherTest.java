package com.bigbread.collector.kakao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GridSearcherTest {

    /** 카카오처럼 한 질의에 최대 45개만 돌려주는 가짜 검색 소스. 모든 키워드가 모든 장소에 맞는다고 본다. */
    static class FakeSource implements PlaceSource {
        final List<KakaoPlace> all = new ArrayList<>();
        final Map<String, Integer> callsPerKeyword = new HashMap<>();
        int calls;

        void add(String id, double lng, double lat) {
            all.add(new KakaoPlace(id, "빵집" + id, "음식점 > 간식 > 제과,베이커리", "", "대전 중구 x", "", lng, lat, ""));
        }

        @Override
        public PlacePage search(String query, Rect rect, int page) {
            calls++;
            callsPerKeyword.merge(query, 1, Integer::sum);
            List<KakaoPlace> inside = all.stream()
                    .filter(p -> p.lng() >= rect.minLng() && p.lng() <= rect.maxLng() && p.lat() >= rect.minLat() && p.lat() <= rect.maxLat())
                    .toList();
            int pageable = Math.min(inside.size(), MAX_RESULTS);
            int from = (page - 1) * PAGE_SIZE;
            List<KakaoPlace> slice = from >= pageable ? List.of() : inside.subList(from, Math.min(from + PAGE_SIZE, pageable));
            return new PlacePage(slice, inside.size(), pageable, from + PAGE_SIZE >= pageable);
        }

        @Override
        public int calls() {
            return calls;
        }
    }

    private static final Rect AREA = new Rect(127.2, 36.2, 127.6, 36.5);

    @Test
    void 구역에_결과가_적으면_쪼개지_않고_한_번씩만_검색한다() {
        FakeSource source = new FakeSource();
        for (int i = 0; i < 10; i++) {
            source.add("p" + i, 127.3 + i * 0.001, 36.3);
        }

        GridSearcher.Result result = new GridSearcher(source, 0.0004).search(AREA, 0.4, List.of("빵집", "베이커리"));

        assertThat(result.places()).hasSize(10); // 두 키워드가 같은 장소를 찾아도 한 번만 센다
        assertThat(result.truncatedCells()).isZero();
        assertThat(result.searchedCells()).isEqualTo(1); // 0.4도 격자: 이 영역(0.4 × 0.3도)은 한 칸
        assertThat(source.calls).isEqualTo(2); // 구역 1 × 키워드 2, 모두 1페이지
    }

    @Test
    void 한도_45개를_넘는_밀집_지역도_쪼개서_빠짐없이_모은다() {
        FakeSource source = new FakeSource();
        Random random = new Random(7);
        for (int i = 0; i < 400; i++) { // 은행동 일대에 400곳이 몰려 있다고 가정
            source.add("p" + i, 127.42 + random.nextDouble() * 0.02, 36.32 + random.nextDouble() * 0.02);
        }

        GridSearcher.Result result = new GridSearcher(source, 0.0004).search(AREA, 0.4, List.of("빵집"));

        assertThat(result.places()).hasSize(400);
        assertThat(result.truncatedCells()).isZero();
    }

    @Test
    void 잘린_키워드만_하위_구역에서_다시_검색한다() {
        FakeSource source = new FakeSource() {
            @Override
            public PlacePage search(String query, Rect rect, int page) {
                // '빵집'만 결과가 많아서 잘리고, '제과점'은 항상 적다
                PlacePage page0 = super.search(query, rect, page);
                return page0;
            }
        };
        for (int i = 0; i < 100; i++) {
            source.add("p" + i, 127.42 + (i % 10) * 0.001, 36.32 + (i / 10) * 0.001);
        }

        new GridSearcher(source, 0.0004).search(AREA, 0.4, List.of("빵집", "제과점"));

        // 두 키워드는 같은 장소를 찾으므로 둘 다 잘리지만, 쪼갠 뒤에도 잘린 것만 다시 묻는다: 두 키워드의 호출 수가 같다.
        assertThat(source.callsPerKeyword.get("빵집")).isEqualTo(source.callsPerKeyword.get("제과점"));
    }

    @Test
    void 더_쪼갤_수_없는_같은_위치의_밀집은_누락을_세어서_알려준다() {
        FakeSource source = new FakeSource();
        for (int i = 0; i < 60; i++) { // 같은 좌표에 60곳 → 아무리 쪼개도 45개까지만 보인다
            source.add("p" + i, 127.4, 36.3);
        }

        GridSearcher.Result result = new GridSearcher(source, 0.0004).search(AREA, 0.4, List.of("빵집"));

        assertThat(result.places()).hasSize(45);
        assertThat(result.truncatedCells()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 구역_경계에_있는_장소가_중복되어_세어지지_않는다() {
        FakeSource source = new FakeSource();
        source.add("edge", 127.4, 36.3); // 4구역이 만나는 점
        for (int i = 0; i < 60; i++) {
            source.add("p" + i, 127.4 + (i % 6) * 0.0001 - 0.0003, 36.3 + (i / 6) * 0.0001 - 0.0003);
        }

        GridSearcher.Result result = new GridSearcher(source, 0.0001).search(new Rect(127.399, 36.299, 127.401, 36.301), 0.002, List.of("빵집"));

        assertThat(result.places()).hasSize(61);
    }
}
