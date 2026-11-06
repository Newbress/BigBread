package com.bigbread.api.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InstagramHandlesTest {

    @Test
    void 계정_주소_골뱅이_형태를_계정명으로_정리한다() {
        assertThat(InstagramHandles.normalize("@Bread_Lab")).isEqualTo("bread_lab");
        assertThat(InstagramHandles.normalize("https://www.instagram.com/Bread.Lab/?hl=ko")).isEqualTo("bread.lab");
        assertThat(InstagramHandles.normalize("instagram.com/bakery_01")).isEqualTo("bakery_01");
        assertThat(InstagramHandles.normalize("  daebbang  ")).isEqualTo("daebbang");
    }

    @Test
    void 비어_있으면_null이고_이상한_값은_거부한다() {
        assertThat(InstagramHandles.normalize(null)).isNull();
        assertThat(InstagramHandles.normalize("   ")).isNull();
        assertThatThrownBy(() -> InstagramHandles.normalize("빵집 계정")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstagramHandles.normalize("a".repeat(31))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstagramHandles.normalize("javascript:alert(1)")).isInstanceOf(IllegalArgumentException.class);
    }
}
