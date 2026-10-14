package com.bigbread.api.route;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KakaoWalkingResponseParserTest {

    private static final String OK = """
            {"route":{"properties":{"totalDistance":4025,"totalTime":3914,"landingUrl":"https://map.kakao.com/x"},
             "legs":[{"properties":{"distance":4025,"time":3914},
              "steps":[
               {"properties":{"distance":93,"guidance":"a","time":84,"x":127.1,"y":37.3},
                "path":{"points":[[127.11119783,37.39477573],[127.11119779,37.39474869],[127.1114835,37.39474572]]}},
               {"properties":{"distance":50,"guidance":"b","time":40,"x":127.1,"y":37.3},
                "path":{"points":[[127.1114835,37.39474572],[127.11148211,37.39533499]]}}
              ]}]},
             "status":"OK"}
            """;

    @Test
    void 거리_시간_경로를_읽고_좌표는_위도_경도_순으로_바꾼다() {
        WalkLeg leg = KakaoWalkingResponseParser.parse(OK).orElseThrow();

        assertThat(leg.distanceMeters()).isEqualTo(4025);
        assertThat(leg.durationMinutes()).isEqualTo(66); // 3914초 → 65.2분 → 올림
        assertThat(leg.estimated()).isFalse();
        assertThat(leg.path().get(0)).isEqualTo(new Point(37.39477573, 127.11119783));
    }

    @Test
    void 단계가_만나는_지점의_중복_좌표는_하나로_합친다() {
        WalkLeg leg = KakaoWalkingResponseParser.parse(OK).orElseThrow();
        assertThat(leg.path()).hasSize(4);
    }

    @Test
    void 상태가_OK가_아니거나_경로가_없으면_빈_값이다() {
        assertThat(KakaoWalkingResponseParser.parse("{\"status\":\"TOO_FAR_AWAY\"}")).isEmpty();
        assertThat(KakaoWalkingResponseParser.parse("{\"status\":\"OK\"}")).isEmpty();
        assertThat(KakaoWalkingResponseParser.parse(
                "{\"status\":\"OK\",\"route\":{\"properties\":{\"totalDistance\":1,\"totalTime\":1},\"legs\":[]}}"))
                .isEmpty();
    }
}
