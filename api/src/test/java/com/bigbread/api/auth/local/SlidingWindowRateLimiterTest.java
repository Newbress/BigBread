package com.bigbread.api.auth.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    /** 테스트에서 시간을 직접 움직이는 시계 */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(clock);
    private static final Duration WINDOW = Duration.ofMinutes(10);

    @Test
    void 한도까지만_허용하고_넘으면_거부한다() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        }
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isFalse();
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isFalse();
    }

    @Test
    void 윈도우가_지나면_다시_허용한다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("k", 3, WINDOW);
        }
        clock.advance(WINDOW.minusSeconds(1));
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isFalse();
        clock.advance(Duration.ofSeconds(2));
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
    }

    @Test
    void 슬라이딩이라_오래된_기록부터_빠진다() {
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isTrue(); // t=0
        clock.advance(Duration.ofMinutes(6));
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isTrue(); // t=6
        clock.advance(Duration.ofMinutes(5)); // t=11: 첫 기록(t=0)만 빠졌다
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isFalse();
    }

    @Test
    void 거부된_시도는_기록하지_않아서_차단_시간을_늘리지_않는다() {
        limiter.tryAcquire("k", 1, WINDOW);
        for (int i = 0; i < 50; i++) {
            assertThat(limiter.tryAcquire("k", 1, WINDOW)).isFalse();
        }
        clock.advance(WINDOW.plusSeconds(1));
        assertThat(limiter.tryAcquire("k", 1, WINDOW)).isTrue();
    }

    @Test
    void 키마다_따로_센다() {
        limiter.tryAcquire("a", 1, WINDOW);
        assertThat(limiter.tryAcquire("a", 1, WINDOW)).isFalse();
        assertThat(limiter.tryAcquire("b", 1, WINDOW)).isTrue();
    }

    @Test
    void 비우면_처음부터_센다() {
        limiter.tryAcquire("k", 1, WINDOW);
        limiter.clear();
        assertThat(limiter.tryAcquire("k", 1, WINDOW)).isTrue();
    }

    @Test
    void 쌓인_키가_많아지면_오래된_기록을_정리한다() {
        for (int i = 0; i < 10_000; i++) {
            limiter.tryAcquire("old-" + i, 1, Duration.ofMinutes(1));
        }
        clock.advance(Duration.ofDays(2));
        limiter.tryAcquire("trigger-1", 1, Duration.ofMinutes(1)); // 임계값(10,000)을 넘기는 첫 호출
        limiter.tryAcquire("trigger-2", 1, Duration.ofMinutes(1)); // 정리가 일어나는 호출
        assertThat(limiter.size()).isLessThan(100);
    }
}
