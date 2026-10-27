package com.bigbread.api.review;

import java.util.Collection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 빵집의 평점·인기 점수 집계를 다시 계산한다 (계산식은 DB 함수 {@code recalc_bakery_stats}, V4 마이그레이션).
 * 즐겨찾기·코스·리뷰가 바뀌는 쪽에서, 같은 트랜잭션 안에서 호출한다.
 */
@Component
public class BakeryStats {

    private final JdbcClient jdbc;

    public BakeryStats(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void recalculate(Collection<Long> bakeryIds) {
        for (Long id : bakeryIds) {
            jdbc.sql("SELECT recalc_bakery_stats(:id)").param("id", id).query().singleRow();
        }
    }

    public void recalculate(long bakeryId) {
        recalculate(java.util.List.of(bakeryId));
    }
}
