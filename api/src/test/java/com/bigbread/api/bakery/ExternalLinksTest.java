package com.bigbread.api.bakery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExternalLinksTest {

    @Test
    void 이름과_도로명주소로_네이버_지도_검색_링크를_만든다() {
        assertThat(ExternalLinks.naverMapSearch("성심당", "대전 중구 대종로480번길 15", "대전 중구 은행동 145"))
                .isEqualTo("https://map.naver.com/p/search/%EC%84%B1%EC%8B%AC%EB%8B%B9%20%EB%8C%80%EC%A0%84%20%EC%A4%91%EA%B5%AC%20%EB%8C%80%EC%A2%85%EB%A1%9C480%EB%B2%88%EA%B8%B8%2015");
    }

    @Test
    void 도로명주소가_없으면_지번주소를_쓰고_둘_다_없으면_이름만_쓴다() {
        assertThat(ExternalLinks.naverMapSearch("가게", null, "대전 중구 은행동 145")).contains("%EC%9D%80%ED%96%89%EB%8F%99");
        assertThat(ExternalLinks.naverMapSearch("가게", " ", null)).isEqualTo("https://map.naver.com/p/search/%EA%B0%80%EA%B2%8C");
    }

    @Test
    void 이름의_특수문자는_링크_구조를_깨지_않게_인코딩한다() {
        String url = ExternalLinks.naverMapSearch("A/B?C#D&E", null, null);

        String path = url.substring("https://map.naver.com/p/search/".length());
        assertThat(path).doesNotContain("/").doesNotContain("?").doesNotContain("#");
        assertThat(url).startsWith("https://map.naver.com/p/search/");
    }
}
