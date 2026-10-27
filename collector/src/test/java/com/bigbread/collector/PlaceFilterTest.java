package com.bigbread.collector;

import static org.assertj.core.api.Assertions.assertThat;

import com.bigbread.collector.kakao.KakaoPlace;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceFilterTest {

    private static final Map<String, Short> DISTRICTS = Map.of("동구", (short) 1, "중구", (short) 2, "서구", (short) 3,
            "유성구", (short) 4, "대덕구", (short) 5);

    private final PlaceFilter filter = new PlaceFilter(new CollectorProperties("", false, null, null, null, null, null), DISTRICTS);

    private static KakaoPlace place(String name, String category, String address) {
        return new KakaoPlace("1", name, category, "", address, "", 127.4, 36.3, "");
    }

    @Test
    void 대전_5개_구의_빵집_카테고리는_통과하고_구_ID를_알려준다() {
        KakaoPlace p = place("성심당", "음식점 > 간식 > 제과,베이커리", "대전 중구 은행동 145");
        assertThat(filter.decide(p)).isEqualTo(PlaceFilter.Decision.ACCEPT);
        assertThat(filter.districtOf(p)).isEqualTo((short) 2);
        assertThat(filter.districtOf(place("x", "x", "대전광역시 유성구 궁동"))).isEqualTo((short) 4);
    }

    @Test
    void 대전_밖이거나_구를_알_수_없으면_영역_밖이다() {
        assertThat(filter.decide(place("빵집", "음식점 > 간식 > 제과,베이커리", "세종특별자치시 조치원읍"))).isEqualTo(PlaceFilter.Decision.OUTSIDE_AREA);
        assertThat(filter.decide(place("빵집", "음식점 > 간식 > 제과,베이커리", "충남 계룡시 금암동"))).isEqualTo(PlaceFilter.Decision.OUTSIDE_AREA);
        assertThat(filter.decide(place("빵집", "음식점 > 간식 > 제과,베이커리", "대전"))).isEqualTo(PlaceFilter.Decision.OUTSIDE_AREA);
        assertThat(filter.decide(place("빵집", "음식점 > 간식 > 제과,베이커리", "서울 강남구 역삼동"))).isEqualTo(PlaceFilter.Decision.OUTSIDE_AREA);
    }

    @Test
    void 빵집_카테고리가_아니면_거른다() {
        assertThat(filter.decide(place("카페", "음식점 > 카페 > 커피전문점", "대전 서구 둔산동"))).isEqualTo(PlaceFilter.Decision.NOT_BAKERY);
        assertThat(filter.decide(place("식당", "음식점 > 한식", "대전 서구 둔산동"))).isEqualTo(PlaceFilter.Decision.NOT_BAKERY);
    }

    @Test
    void 대형_프랜차이즈는_이름으로_제외한다_공백과_대소문자는_무시한다() {
        for (String name : new String[] {"파리바게뜨 둔산점", "뚜레쥬르 대전탄방점", "던킨 은행점", "파리 바게뜨 서구점"}) {
            assertThat(filter.decide(place(name, "음식점 > 간식 > 제과,베이커리", "대전 서구 둔산동"))).as(name).isEqualTo(PlaceFilter.Decision.EXCLUDED_NAME);
        }
        assertThat(filter.decide(place("성심당 롯데백화점점", "음식점 > 간식 > 제과,베이커리", "대전 서구 둔산동"))).isEqualTo(PlaceFilter.Decision.ACCEPT);
    }

    @Test
    void 제외_이름_목록은_설정으로_바꿀_수_있다() {
        CollectorProperties none = new CollectorProperties("", false, null, null, null, null, java.util.List.of());
        PlaceFilter all = new PlaceFilter(none, DISTRICTS);

        assertThat(all.decide(place("파리바게뜨 둔산점", "음식점 > 간식 > 제과,베이커리", "대전 서구 둔산동"))).isEqualTo(PlaceFilter.Decision.ACCEPT);
    }

    @Test
    void ID_이름_좌표가_없으면_정보_부족이다() {
        assertThat(filter.decide(new KakaoPlace("", "x", "제과,베이커리", "", "대전 중구", "", 127.4, 36.3, ""))).isEqualTo(PlaceFilter.Decision.BAD_DATA);
        assertThat(filter.decide(new KakaoPlace("1", "", "제과,베이커리", "", "대전 중구", "", 127.4, 36.3, ""))).isEqualTo(PlaceFilter.Decision.BAD_DATA);
        assertThat(filter.decide(new KakaoPlace("1", "x", "제과,베이커리", "", "대전 중구", "", Double.NaN, Double.NaN, ""))).isEqualTo(PlaceFilter.Decision.BAD_DATA);
    }
}
