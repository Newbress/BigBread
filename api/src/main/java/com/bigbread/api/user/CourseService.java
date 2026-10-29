package com.bigbread.api.user;

import com.bigbread.api.bakery.BakeryQueryService;
import com.bigbread.api.bakery.BakerySummary;
import com.bigbread.api.common.ApiException;
import com.bigbread.api.review.BakeryStats;
import com.bigbread.api.route.RouteOrder;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장한 코스(동선). 방문 순서가 확정된 빵집 목록을 이름과 함께 저장한다.
 * 출발 위치는 저장하지 않는다 (내 위치 같은 개인 좌표를 서버에 남기지 않으려는 선택).
 */
@Service
public class CourseService {

    public static final int MAX_COURSES = 50;
    public static final int MAX_STOPS = 10;

    public record Summary(long id, String title, String description, RouteOrder order, int stopCount,
                          List<String> stopNames, OffsetDateTime createdAt) {
    }

    public record Detail(long id, String title, String description, RouteOrder order, OffsetDateTime createdAt,
                         List<BakerySummary> stops) {
    }

    private final JdbcClient jdbc;
    private final BakeryQueryService bakeries;
    private final BakeryStats stats;

    public CourseService(JdbcClient jdbc, BakeryQueryService bakeries, BakeryStats stats) {
        this.jdbc = jdbc;
        this.bakeries = bakeries;
        this.stats = stats;
    }

    @Transactional
    public Detail create(long userId, String title, String description, RouteOrder order, List<Long> bakeryIds) {
        if (new HashSet<>(bakeryIds).size() != bakeryIds.size()) {
            throw ApiException.badRequest("bakeryIds에 중복된 빵집이 있어요.");
        }
        Map<Long, BakerySummary> found = summariesById(bakeryIds);
        List<Long> missing = bakeryIds.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("존재하지 않거나 볼 수 없는 빵집이에요: " + missing);
        }
        Long count = jdbc.sql("SELECT count(*) FROM course WHERE user_id = :userId").param("userId", userId).query(Long.class).single();
        if (count >= MAX_COURSES) {
            throw ApiException.badRequest("코스는 최대 " + MAX_COURSES + "개까지 저장할 수 있어요. 안 쓰는 코스를 지워 주세요.");
        }

        long courseId = jdbc.sql("""
                        INSERT INTO course (user_id, title, description, order_mode)
                        VALUES (:userId, :title, :description, :order) RETURNING id
                        """)
                .param("userId", userId)
                .param("title", title)
                .param("description", description)
                .param("order", order.name())
                .query(Long.class)
                .single();
        for (int i = 0; i < bakeryIds.size(); i++) {
            jdbc.sql("INSERT INTO course_stop (course_id, seq, bakery_id) VALUES (:courseId, :seq, :bakeryId)")
                    .param("courseId", courseId).param("seq", i + 1).param("bakeryId", bakeryIds.get(i)).update();
        }
        stats.recalculate(bakeryIds);
        return get(userId, courseId).orElseThrow();
    }

    /** 최근에 만든 순서. 숨김 처리된 빵집은 개수와 이름에서 빠진다. */
    public List<Summary> list(long userId) {
        return jdbc.sql("""
                        SELECT c.id, c.title, c.description, c.order_mode, c.created_at,
                               COALESCE(array_agg(b.name ORDER BY s.seq) FILTER (WHERE b.id IS NOT NULL), '{}') AS names
                          FROM course c
                          LEFT JOIN course_stop s ON s.course_id = c.id
                          LEFT JOIN bakery b ON b.id = s.bakery_id AND b.status = 'ACTIVE'
                         WHERE c.user_id = :userId
                         GROUP BY c.id
                         ORDER BY c.created_at DESC, c.id DESC
                        """)
                .param("userId", userId)
                .query((rs, n) -> {
                    String[] names = (String[]) rs.getArray("names").getArray();
                    return new Summary(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
                            RouteOrder.valueOf(rs.getString("order_mode")), names.length, List.of(names),
                            rs.getObject("created_at", OffsetDateTime.class));
                })
                .list();
    }

    /** 내 코스만 볼 수 있다. 남의 코스나 없는 코스는 모두 빈 값(→ 404). */
    public Optional<Detail> get(long userId, long courseId) {
        record Head(long id, String title, String description, RouteOrder order, OffsetDateTime createdAt) {
        }
        Optional<Head> head = jdbc.sql("""
                        SELECT id, title, description, order_mode, created_at
                          FROM course WHERE id = :id AND user_id = :userId
                        """)
                .param("id", courseId).param("userId", userId)
                .query((rs, n) -> new Head(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
                        RouteOrder.valueOf(rs.getString("order_mode")), rs.getObject("created_at", OffsetDateTime.class)))
                .optional();
        if (head.isEmpty()) {
            return Optional.empty();
        }
        List<Long> ids = jdbc.sql("SELECT bakery_id FROM course_stop WHERE course_id = :id ORDER BY seq")
                .param("id", courseId).query(Long.class).list();
        Map<Long, BakerySummary> byId = summariesById(ids);
        List<BakerySummary> stops = ids.stream().filter(byId::containsKey).map(byId::get).toList();
        Head h = head.get();
        return Optional.of(new Detail(h.id(), h.title(), h.description(), h.order(), h.createdAt(), stops));
    }

    /** @return 바꿨으면 true, 내 코스가 아니면 false */
    @Transactional
    public boolean rename(long userId, long courseId, String title, String description) {
        return jdbc.sql("""
                        UPDATE course SET title = :title, description = :description, updated_at = now()
                         WHERE id = :id AND user_id = :userId
                        """)
                .param("title", title).param("description", description)
                .param("id", courseId).param("userId", userId)
                .update() > 0;
    }

    /** @return 지웠으면 true, 내 코스가 아니면 false */
    @Transactional
    public boolean delete(long userId, long courseId) {
        List<Long> bakeryIds = jdbc.sql("""
                        SELECT s.bakery_id FROM course_stop s JOIN course c ON c.id = s.course_id
                         WHERE c.id = :id AND c.user_id = :userId
                        """)
                .param("id", courseId).param("userId", userId).query(Long.class).list();
        boolean deleted = jdbc.sql("DELETE FROM course WHERE id = :id AND user_id = :userId")
                .param("id", courseId).param("userId", userId).update() > 0;
        if (deleted) {
            stats.recalculate(bakeryIds);
        }
        return deleted;
    }

    private Map<Long, BakerySummary> summariesById(List<Long> ids) {
        Map<Long, BakerySummary> byId = new HashMap<>();
        bakeries.findSummariesByIds(ids).forEach(b -> byId.put(b.id(), b));
        return byId;
    }
}
