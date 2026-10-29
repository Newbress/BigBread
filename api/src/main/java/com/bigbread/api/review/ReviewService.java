package com.bigbread.api.review;

import com.bigbread.api.bakery.BakeryQueryService;
import com.bigbread.api.bakery.BakerySummary;
import com.bigbread.api.common.ApiException;
import com.bigbread.api.common.PageResponse;
import com.bigbread.api.review.photo.PhotoService;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 빵집 리뷰·평점. 사용자당 빵집별 1개이고, 같은 빵집에 다시 쓰면 수정이다. */
@Service
public class ReviewService {

    public static final int MAX_CONTENT_LENGTH = 1000;

    /** 목록에 나가는 리뷰. 작성자는 닉네임만 보이고 내부 ID는 노출하지 않는다. */
    public record Item(long id, int rating, String content, String nickname, boolean mine,
                       List<PhotoService.Photo> photos, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

        Item withPhotos(List<PhotoService.Photo> photos) {
            return new Item(id, rating, content, nickname, mine, photos, createdAt, updatedAt);
        }
    }

    public record MyReview(long id, BakerySummary bakery, int rating, String content,
                           OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    private final JdbcClient jdbc;
    private final BakeryQueryService bakeries;
    private final BakeryStats stats;
    private final PhotoService photos;

    public ReviewService(JdbcClient jdbc, BakeryQueryService bakeries, BakeryStats stats, PhotoService photos) {
        this.photos = photos;
        this.jdbc = jdbc;
        this.bakeries = bakeries;
        this.stats = stats;
    }

    /** 최신순. 숨김 처리된 리뷰는 나오지 않는다. viewerId는 비로그인이면 null. */
    public PageResponse<Item> list(long bakeryId, Long viewerId, int page, int size) {
        requireVisibleBakery(bakeryId);
        long total = jdbc.sql("SELECT count(*) FROM review WHERE bakery_id = :b AND status = 'VISIBLE'")
                .param("b", bakeryId).query(Long.class).single();
        List<Item> items = jdbc.sql("""
                        SELECT r.id, r.rating, r.content, u.nickname, r.user_id, r.created_at, r.updated_at
                          FROM review r JOIN app_user u ON u.id = r.user_id
                         WHERE r.bakery_id = :b AND r.status = 'VISIBLE'
                         ORDER BY r.created_at DESC, r.id DESC
                         LIMIT :limit OFFSET :offset
                        """)
                .param("b", bakeryId).param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> new Item(rs.getLong("id"), rs.getInt("rating"), rs.getString("content"),
                        rs.getString("nickname"), viewerId != null && viewerId == rs.getLong("user_id"), List.of(),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)))
                .list();
        Map<Long, List<PhotoService.Photo>> byReview = photos.visibleByReview(items.stream().map(Item::id).toList());
        items = items.stream().map(i -> i.withPhotos(byReview.getOrDefault(i.id(), List.of()))).toList();
        return PageResponse.of(items, page, size, total);
    }

    /** 내가 이 빵집에 쓴 리뷰 (숨김 처리된 것도 본인에게는 보인다). */
    public Optional<Item> mine(long userId, long bakeryId) {
        return jdbc.sql("""
                        SELECT r.id, r.rating, r.content, u.nickname, r.created_at, r.updated_at
                          FROM review r JOIN app_user u ON u.id = r.user_id
                         WHERE r.bakery_id = :b AND r.user_id = :u
                        """)
                .param("b", bakeryId).param("u", userId)
                .query((rs, n) -> new Item(rs.getLong("id"), rs.getInt("rating"), rs.getString("content"),
                        rs.getString("nickname"), true, List.of(),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)))
                .optional()
                .map(item -> item.withPhotos(photos.visibleByReview(List.of(item.id())).getOrDefault(item.id(), List.of())));
    }

    /** 내가 쓴 리뷰 전체, 최근에 쓴 순서. 숨김 처리된 빵집의 리뷰는 빠진다. */
    public List<MyReview> listMine(long userId) {
        record Row(long id, long bakeryId, int rating, String content, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        }
        List<Row> rows = jdbc.sql("""
                        SELECT id, bakery_id, rating, content, created_at, updated_at FROM review
                         WHERE user_id = :u ORDER BY updated_at DESC, id DESC
                        """)
                .param("u", userId)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getLong("bakery_id"), rs.getInt("rating"),
                        rs.getString("content"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)))
                .list();
        Map<Long, BakerySummary> byId = new HashMap<>();
        bakeries.findSummariesByIds(rows.stream().map(Row::bakeryId).toList()).forEach(b -> byId.put(b.id(), b));
        return rows.stream().filter(r -> byId.containsKey(r.bakeryId()))
                .map(r -> new MyReview(r.id(), byId.get(r.bakeryId()), r.rating(), r.content(), r.createdAt(), r.updatedAt()))
                .toList();
    }

    /** 처음이면 만들고, 이미 있으면 수정한다 (멱등). 수정하면 숨김 상태는 그대로 유지한다. */
    @Transactional
    public Item save(long userId, long bakeryId, int rating, String content) {
        requireVisibleBakery(bakeryId);
        if (rating < 1 || rating > 5) {
            throw ApiException.badRequest("평점은 1~5점이에요.");
        }
        String text = content == null || content.isBlank() ? null : content.strip();
        if (text != null && text.length() > MAX_CONTENT_LENGTH) {
            throw ApiException.badRequest("리뷰는 " + MAX_CONTENT_LENGTH + "자까지 쓸 수 있어요.");
        }
        jdbc.sql("""
                        INSERT INTO review (bakery_id, user_id, rating, content) VALUES (:b, :u, :rating, :content)
                        ON CONFLICT (bakery_id, user_id)
                        DO UPDATE SET rating = EXCLUDED.rating, content = EXCLUDED.content, updated_at = now()
                        """)
                .param("b", bakeryId).param("u", userId).param("rating", rating).param("content", text)
                .update();
        stats.recalculate(bakeryId);
        return mine(userId, bakeryId).orElseThrow();
    }

    /** 없어도 오류 없이 끝난다 (멱등). */
    @Transactional
    public void delete(long userId, long bakeryId) {
        List<Long> reviewIds = jdbc.sql("SELECT id FROM review WHERE bakery_id = :b AND user_id = :u")
                .param("b", bakeryId).param("u", userId).query(Long.class).list();
        photos.deleteFilesAfterCommit(reviewIds);
        int deleted = jdbc.sql("DELETE FROM review WHERE bakery_id = :b AND user_id = :u")
                .param("b", bakeryId).param("u", userId).update();
        if (deleted > 0) {
            stats.recalculate(bakeryId);
        }
    }

    private void requireVisibleBakery(long bakeryId) {
        if (bakeries.findSummariesByIds(List.of(bakeryId)).isEmpty()) {
            throw ApiException.notFound("빵집을 찾을 수 없어요.");
        }
    }
}
