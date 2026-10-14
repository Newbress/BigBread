package com.bigbread.api.route;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KakaoMapLinksTest {

    @Test
    void 이름과_좌표를_카카오맵_도보_링크_형식으로_만든다() {
        String url = KakaoMapLinks.walk("출발지", new Point(36.3283, 127.428), "성심빵집", new Point(36.3326, 127.4342));

        assertThat(url).isEqualTo("https://map.kakao.com/link/by/walk/"
                + "%EC%B6%9C%EB%B0%9C%EC%A7%80,36.328300,127.428000/"
                + "%EC%84%B1%EC%8B%AC%EB%B9%B5%EC%A7%91,36.332600,127.434200");
    }

    @Test
    void 이름의_구분자_문자는_공백으로_바꿔서_링크_구조를_깨지_않는다() {
        String url = KakaoMapLinks.walk("A,B/C", new Point(36.0, 127.0), "D?E#F", new Point(36.1, 127.1));

        // 경로 구분자(/)는 출발/도착 사이의 하나뿐이고, 쉼표는 이름-위도-경도 사이의 두 개뿐이어야 한다.
        String path = url.substring("https://map.kakao.com/link/by/walk/".length());
        assertThat(path.split("/")).hasSize(2);
        for (String place : path.split("/")) {
            assertThat(place.split(",")).hasSize(3);
        }
        assertThat(url).doesNotContain("?").doesNotContain("#");
    }

    @Test
    void 좌표는_로케일과_상관없이_소수점_6자리다() {
        String url = KakaoMapLinks.walk("a", new Point(36.5, 127.25), "b", new Point(36.5, 127.25));
        assertThat(url).contains("36.500000,127.250000");
    }
}
