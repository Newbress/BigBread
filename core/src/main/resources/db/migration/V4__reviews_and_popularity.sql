-- 리뷰 운영 상태 (신고 누적/관리자 처리로 숨길 때 사용)
ALTER TABLE review
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN'));

-- 목록 정렬/표시용 집계 (review, favorite, course_stop에서 계산해 채운다)
ALTER TABLE bakery
    ADD COLUMN review_count INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN rating_avg   NUMERIC(3, 2);

-- 빵집 하나의 평점/인기 점수를 다시 계산한다.
--   인기 점수 = 즐겨찾기 x 2 + 코스에 담긴 수 x 1 + 리뷰 수 x 보정 평균 평점
--   보정 평균 = (3 x 3.5 + 평점 합) / (3 + 리뷰 수)  -- 리뷰가 적을 때 한두 개로 쏠리지 않게 한다
CREATE FUNCTION recalc_bakery_stats(p_bakery_id BIGINT) RETURNS VOID
    LANGUAGE sql AS
$$
UPDATE bakery b
   SET review_count     = s.n,
       rating_avg       = CASE WHEN s.n > 0 THEN round((s.total::numeric / s.n), 2) END,
       popularity_score = round((2 * s.fav + s.course + s.n * ((3 * 3.5 + s.total) / (3 + s.n)))::numeric, 2)
  FROM (SELECT (SELECT count(*) FROM favorite f WHERE f.bakery_id = p_bakery_id)                  AS fav,
               (SELECT count(DISTINCT cs.course_id) FROM course_stop cs WHERE cs.bakery_id = p_bakery_id) AS course,
               (SELECT count(*) FROM review r WHERE r.bakery_id = p_bakery_id AND r.status = 'VISIBLE')   AS n,
               (SELECT COALESCE(sum(r.rating), 0) FROM review r
                 WHERE r.bakery_id = p_bakery_id AND r.status = 'VISIBLE')                        AS total) s
 WHERE b.id = p_bakery_id;
$$;

-- 기존 데이터 반영
SELECT recalc_bakery_stats(id) FROM bakery;
