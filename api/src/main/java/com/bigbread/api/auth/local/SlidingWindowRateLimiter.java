package com.bigbread.api.auth.local;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키별로 "최근 window 동안 limit번까지"만 허용하는 슬라이딩 윈도우 제한기. 서버 메모리에만 저장한다
 * (재시작하면 초기화되고, 서버가 여러 대면 서버마다 따로 센다. 계정 단위 잠금은 DB에 저장되어 그 영향을 받지 않는다).
 */
class SlidingWindowRateLimiter {

    /** 쌓인 키가 이만큼 넘으면 오래된 기록을 정리한다. */
    private static final int PRUNE_THRESHOLD = 10_000;
    /** 어떤 제한도 이보다 긴 윈도우를 쓰지 않는다. 마지막 기록이 이보다 오래되면 버려도 된다. */
    private static final Duration MAX_WINDOW = Duration.ofDays(1);

    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    SlidingWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 허용되면 기록하고 true, 한도를 넘으면 기록하지 않고 false. */
    boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long cutoff = now - window.toMillis();
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() <= cutoff) {
                queue.pollFirst();
            }
            allowed = queue.size() < limit;
            if (allowed) {
                queue.addLast(now);
            }
        }
        if (hits.size() > PRUNE_THRESHOLD) {
            prune(now);
        }
        return allowed;
    }

    void clear() {
        hits.clear();
    }

    int size() {
        return hits.size();
    }

    private void prune(long now) {
        long cutoff = now - MAX_WINDOW.toMillis();
        hits.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Long last = entry.getValue().peekLast();
                return last == null || last <= cutoff;
            }
        });
    }
}
