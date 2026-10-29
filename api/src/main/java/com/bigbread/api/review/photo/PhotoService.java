package com.bigbread.api.review.photo;

import com.bigbread.api.auth.local.SlidingWindowRateLimiter;
import com.bigbread.api.common.ApiException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** 리뷰 사진 올리기·지우기·조회. 사진은 내 리뷰에만 붙일 수 있고, 리뷰 하나에 {@code maxPerReview}장까지다. */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    /** 응답에 나가는 사진. 주소는 저장소 위치가 아니라 API 주소라서, 저장소를 바꿔도 그대로다. */
    public record Photo(long id, String url, String thumbUrl, int width, int height) {
    }

    /** 서빙할 파일. */
    public record Image(byte[] bytes) {
    }

    private final JdbcClient jdbc;
    private final PhotoStorage storage;
    private final PhotoProcessor processor;
    private final PhotoProperties props;
    private final TransactionTemplate tx;
    private final SlidingWindowRateLimiter uploadLimiter = new SlidingWindowRateLimiter(Clock.systemUTC());

    public PhotoService(JdbcClient jdbc, PhotoStorage storage, PhotoProcessor processor, PhotoProperties props,
                        TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.processor = processor;
        this.props = props;
        this.tx = tx;
    }

    public Photo add(long userId, long bakeryId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("사진 파일이 비어 있어요.");
        }
        // 무거운 처리(디코딩·변환)를 하기 전에 올릴 수 있는 상태인지 먼저 확인한다.
        long reviewId = myReviewId(userId, bakeryId).orElseThrow(() -> ApiException.notFound("먼저 리뷰를 써 주세요."));
        requireRoom(reviewId);
        if (!uploadLimiter.tryAcquire("photo:" + userId, props.uploadsPerHour(), Duration.ofHours(1))) {
            throw ApiException.tooManyRequests("사진을 너무 많이 올렸어요. 잠시 후 다시 시도해 주세요.");
        }

        PhotoProcessor.Processed processed;
        try {
            processed = processor.process(file.getBytes());
        } catch (PhotoRejectedException e) {
            throw ApiException.badRequest(e.getMessage());
        } catch (IOException e) {
            throw ApiException.badRequest("사진을 읽지 못했어요. 다시 시도해 주세요.");
        }

        String id = PhotoKeys.newId();
        storage.put(PhotoKeys.main(id), processed.main());
        storage.put(PhotoKeys.thumb(id), processed.thumb());
        try {
            long photoId = tx.execute(status -> {
                // 같은 리뷰에 동시에 올려도 한도를 넘지 않게 리뷰 행을 잠근다.
                jdbc.sql("SELECT id FROM review WHERE id = :id FOR UPDATE").param("id", reviewId).query(Long.class).optional()
                        .orElseThrow(() -> ApiException.notFound("먼저 리뷰를 써 주세요."));
                requireRoom(reviewId);
                return jdbc.sql("""
                                INSERT INTO review_photo (review_id, storage_key, width, height, byte_size, seq)
                                VALUES (:review, :key, :w, :h, :size,
                                        (SELECT COALESCE(max(seq), 0) + 1 FROM review_photo WHERE review_id = :review))
                                RETURNING id
                                """)
                        .param("review", reviewId).param("key", id)
                        .param("w", processed.width()).param("h", processed.height())
                        .param("size", processed.main().length)
                        .query(Long.class).single();
            });
            return toPhoto(photoId, id, processed.width(), processed.height());
        } catch (RuntimeException e) {
            deleteFiles(id);
            throw e;
        }
    }

    /** 내 리뷰의 사진만 지운다. 없거나 남의 것이면 아무 일도 없다 (멱등). */
    public void delete(long userId, long bakeryId, long photoId) {
        Optional<String> key = jdbc.sql("""
                        SELECT p.storage_key FROM review_photo p JOIN review r ON r.id = p.review_id
                         WHERE p.id = :photo AND r.bakery_id = :bakery AND r.user_id = :user
                        """)
                .param("photo", photoId).param("bakery", bakeryId).param("user", userId)
                .query(String.class).optional();
        if (key.isEmpty()) {
            return;
        }
        tx.executeWithoutResult(status -> jdbc.sql("DELETE FROM review_photo WHERE id = :id").param("id", photoId).update());
        deleteFiles(key.get());
    }

    /** 리뷰들의 보이는 사진 (리뷰 ID별, 올린 순서). */
    public Map<Long, List<Photo>> visibleByReview(Collection<Long> reviewIds) {
        Map<Long, List<Photo>> result = new HashMap<>();
        if (reviewIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                        SELECT id, review_id, storage_key, width, height FROM review_photo
                         WHERE review_id IN (:ids) AND status = 'VISIBLE' ORDER BY review_id, seq
                        """)
                .param("ids", reviewIds)
                .query((rs, n) -> {
                    result.computeIfAbsent(rs.getLong("review_id"), k -> new ArrayList<>())
                            .add(toPhoto(rs.getLong("id"), rs.getString("storage_key"), rs.getInt("width"), rs.getInt("height")));
                    return null;
                }).list();
        return result;
    }

    /** 리뷰(들)을 지우기 전에 호출해서 파일 이름을 모아 두고, 지운 트랜잭션이 확정된 뒤 파일을 지운다. */
    public void deleteFilesAfterCommit(Collection<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return;
        }
        List<String> ids = jdbc.sql("SELECT storage_key FROM review_photo WHERE review_id IN (:ids)")
                .param("ids", reviewIds).query(String.class).list();
        if (ids.isEmpty()) {
            return;
        }
        Runnable cleanup = () -> ids.forEach(this::deleteFiles);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    /** 공개된(VISIBLE) 사진의 파일. 숨김·없는 사진은 비어 있다. */
    public Optional<Image> load(String id, boolean thumb) {
        if (!PhotoKeys.isValidId(id)) {
            return Optional.empty();
        }
        boolean visible = jdbc.sql("SELECT EXISTS (SELECT 1 FROM review_photo WHERE storage_key = :key AND status = 'VISIBLE')")
                .param("key", id).query(Boolean.class).single();
        if (!visible) {
            return Optional.empty();
        }
        return storage.read(thumb ? PhotoKeys.thumb(id) : PhotoKeys.main(id)).map(Image::new);
    }

    private Optional<Long> myReviewId(long userId, long bakeryId) {
        return jdbc.sql("SELECT id FROM review WHERE bakery_id = :b AND user_id = :u")
                .param("b", bakeryId).param("u", userId).query(Long.class).optional();
    }

    private void requireRoom(long reviewId) {
        Long count = jdbc.sql("SELECT count(*) FROM review_photo WHERE review_id = :id").param("id", reviewId)
                .query(Long.class).single();
        if (count >= props.maxPerReview()) {
            throw ApiException.badRequest("사진은 리뷰 하나에 최대 " + props.maxPerReview() + "장까지 올릴 수 있어요.");
        }
    }

    private void deleteFiles(String id) {
        for (String key : List.of(PhotoKeys.main(id), PhotoKeys.thumb(id))) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("사진 파일을 지우지 못했어요 ({}): {}", key, e.getMessage());
            }
        }
    }

    private static Photo toPhoto(long id, String key, int width, int height) {
        return new Photo(id, "/api/photos/" + key, "/api/photos/" + key + "/thumb", width, height);
    }
}
