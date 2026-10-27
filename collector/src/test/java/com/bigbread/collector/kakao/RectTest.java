package com.bigbread.collector.kakao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RectTest {

    @Test
    void 카카오_파라미터는_좌_하_우_상_순서의_소수_6자리다() {
        assertThat(new Rect(127.3, 36.3, 127.4, 36.4).toParam()).isEqualTo("127.300000,36.300000,127.400000,36.400000");
    }

    @Test
    void 네_구역으로_나누면_합쳐서_원래_영역이_된다() {
        Rect rect = new Rect(127.0, 36.0, 127.2, 36.4);
        List<Rect> parts = rect.splitInFour();

        assertThat(parts).hasSize(4);
        assertThat(parts.stream().mapToDouble(r -> r.width() * r.height()).sum())
                .isCloseTo(rect.width() * rect.height(), org.assertj.core.data.Offset.offset(1e-12));
        assertThat(parts).allSatisfy(r -> {
            assertThat(r.width()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(1e-12));
            assertThat(r.height()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-12));
        });
    }

    @Test
    void 격자는_영역_전체를_덮고_가장자리_구역은_영역을_넘지_않는다() {
        Rect area = new Rect(127.0, 36.0, 127.25, 36.15);
        List<Rect> cells = area.grid(0.1);

        assertThat(cells).hasSize(6); // 가로 3 × 세로 2
        assertThat(cells.stream().mapToDouble(Rect::maxLng).max().orElseThrow()).isEqualTo(127.25);
        assertThat(cells.stream().mapToDouble(Rect::maxLat).max().orElseThrow()).isEqualTo(36.15);
        assertThat(cells.stream().mapToDouble(Rect::minLng).min().orElseThrow()).isEqualTo(127.0);
    }
}
