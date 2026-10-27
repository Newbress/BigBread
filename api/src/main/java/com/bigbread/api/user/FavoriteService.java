package com.bigbread.api.user;

import com.bigbread.api.bakery.BakeryQueryService;
import com.bigbread.api.bakery.BakerySummary;
import com.bigbread.api.common.ApiException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자의 즐겨찾기 빵집. 숨김 처리된 빵집은 목록에 나오지 않는다. */
@Service
public class FavoriteService {

    /** 한 사용자가 담을 수 있는 최대 개수 */
    public static final int MAX_FAVORITES = 300;

    public record Item(BakerySummary bakery, OffsetDateTime favoritedAt) {
    }

    private final JdbcClient jdbc;
    private final BakeryQueryService bakeries;

    public FavoriteService(JdbcClient jdbc, BakeryQueryService bakeries) {
        this.jdbc = jdbc;
        this.bakeries = bakeries;
    }

    /** 최근에 담은 순서. */
    public List<Item> list(long userId) {
        record Row(long bakeryId, OffsetDateTime createdAt) {
        }
        List<Row> rows = jdbc.sql("SELECT bakery_id, created_at FROM favorite WHERE user_id = :userId ORDER BY created_at DESC, bakery_id DESC")
                .param("userId", userId)
                .query((rs, n) -> new Row(rs.getLong("bakery_id"), rs.getObject("created_at", OffsetDateTime.class)))
                .list();
        Map<Long, BakerySummary> byId = new HashMap<>();
        bakeries.findSummariesByIds(rows.stream().map(Row::bakeryId).toList()).forEach(b -> byId.put(b.id(), b));
        return rows.stream()
                .filter(row -> byId.containsKey(row.bakeryId()))
                .map(row -> new Item(byId.get(row.bakeryId()), row.createdAt()))
                .toList();
    }

    /** 이미 담겨 있어도 오류 없이 끝난다 (멱등). */
    @Transactional
    public void add(long userId, long bakeryId) {
        if (bakeries.findSummariesByIds(List.of(bakeryId)).isEmpty()) {
            throw ApiException.notFound("빵집을 찾을 수 없어요.");
        }
        Long count = jdbc.sql("SELECT count(*) FROM favorite WHERE user_id = :userId AND bakery_id <> :bakeryId")
                .param("userId", userId).param("bakeryId", bakeryId).query(Long.class).single();
        if (count >= MAX_FAVORITES) {
            throw ApiException.badRequest("즐겨찾기는 최대 " + MAX_FAVORITES + "곳까지 담을 수 있어요.");
        }
        jdbc.sql("INSERT INTO favorite (user_id, bakery_id) VALUES (:userId, :bakeryId) ON CONFLICT DO NOTHING")
                .param("userId", userId).param("bakeryId", bakeryId).update();
    }

    /** 담겨 있지 않아도 오류 없이 끝난다 (멱등). */
    public void remove(long userId, long bakeryId) {
        jdbc.sql("DELETE FROM favorite WHERE user_id = :userId AND bakery_id = :bakeryId")
                .param("userId", userId).param("bakeryId", bakeryId).update();
    }
}
