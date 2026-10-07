-- 개발용 샘플 데이터 (spring.profiles.active=dev 일 때만 적용). 실제 가게 정보가 아니다.
-- 반복 마이그레이션(R__)이고 kakao_place_id = 'sample-NN' 으로 멱등하게 넣는다.

INSERT INTO bakery (name, district_id, road_address, location, kakao_place_id, instagram_username,
                    opening_hours, popularity_score)
SELECT v.name, v.district_id, v.road_address, ST_SetSRID(ST_MakePoint(v.lng, v.lat), 4326), v.place_id,
       v.instagram, v.hours::jsonb, v.score
FROM (VALUES
    ('[샘플] 대전역 소금빵집',   1, '대전 동구 중앙로 (샘플)',     36.3326, 127.4342, 'sample-01', 'sample_bakery_01',
        '{"mon":"08:00-21:00","tue":"08:00-21:00","wed":"08:00-21:00","thu":"08:00-21:00","fri":"08:00-21:00","sat":"09:00-21:00","sun":"09:00-18:00"}', 88),
    ('[샘플] 용전동 식빵공방',   1, '대전 동구 용전동 (샘플)',     36.3500, 127.4400, 'sample-02', NULL,
        '{"mon":"closed","tue":"10:00-19:00","wed":"10:00-19:00","thu":"10:00-19:00","fri":"10:00-19:00","sat":"10:00-19:00","sun":"10:00-17:00"}', 52),
    ('[샘플] 은행동 튀김소보로', 2, '대전 중구 은행동 (샘플)',     36.3283, 127.4280, 'sample-03', 'sample_bakery_03',
        '{"mon":"07:30-22:00","tue":"07:30-22:00","wed":"07:30-22:00","thu":"07:30-22:00","fri":"07:30-22:00","sat":"07:30-22:00","sun":"07:30-22:00"}', 97),
    ('[샘플] 대흥동 앙버터',     2, '대전 중구 대흥동 (샘플)',     36.3262, 127.4204, 'sample-04', NULL,
        '{"mon":"11:00-20:00","tue":"11:00-20:00","wed":"closed","thu":"11:00-20:00","fri":"11:00-20:00","sat":"11:00-20:00","sun":"11:00-20:00"}', 64),
    ('[샘플] 둔산 크루아상',     3, '대전 서구 둔산동 (샘플)',     36.3504, 127.3845, 'sample-05', 'sample_bakery_05',
        '{"mon":"08:00-20:00","tue":"08:00-20:00","wed":"08:00-20:00","thu":"08:00-20:00","fri":"08:00-20:00","sat":"09:00-20:00","sun":"closed"}', 79),
    ('[샘플] 탄방 쌀빵집',       3, '대전 서구 탄방동 (샘플)',     36.3414, 127.3874, 'sample-06', NULL,
        '{"mon":"09:00-19:00","tue":"09:00-19:00","wed":"09:00-19:00","thu":"09:00-19:00","fri":"09:00-19:00","sat":"09:00-17:00","sun":"closed"}', 41),
    ('[샘플] 궁동 마늘빵',       4, '대전 유성구 궁동 (샘플)',     36.3622, 127.3568, 'sample-07', 'sample_bakery_07',
        '{"mon":"10:00-22:00","tue":"10:00-22:00","wed":"10:00-22:00","thu":"10:00-22:00","fri":"10:00-23:00","sat":"10:00-23:00","sun":"10:00-22:00"}', 85),
    ('[샘플] 도안 베이글',       4, '대전 유성구 도안동 (샘플)',   36.3190, 127.3430, 'sample-08', NULL,
        '{"mon":"08:00-18:00","tue":"08:00-18:00","wed":"08:00-18:00","thu":"08:00-18:00","fri":"08:00-18:00","sat":"08:00-18:00","sun":"08:00-16:00"}', 58),
    ('[샘플] 송촌 단팥빵',       5, '대전 대덕구 송촌동 (샘플)',   36.3560, 127.4290, 'sample-09', NULL,
        '{"mon":"07:00-20:00","tue":"07:00-20:00","wed":"07:00-20:00","thu":"07:00-20:00","fri":"07:00-20:00","sat":"07:00-20:00","sun":"closed"}', 47),
    ('[샘플] 대덕구청 앞 깜빠뉴', 5, '대전 대덕구 오정동 (샘플)', 36.3466, 127.4155, 'sample-10', 'sample_bakery_10',
        '{"mon":"closed","tue":"09:00-18:00","wed":"09:00-18:00","thu":"09:00-18:00","fri":"09:00-18:00","sat":"09:00-18:00","sun":"09:00-15:00"}', 33)
) AS v(name, district_id, road_address, lat, lng, place_id, instagram, hours, score)
ON CONFLICT (kakao_place_id) DO NOTHING;

INSERT INTO bread (bakery_id, name, description, price, signature)
SELECT b.id, v.bread, v.descr, v.price, v.sig
FROM (VALUES
    ('sample-01', '소금빵',        '겉은 바삭, 속은 촉촉한 버터 소금빵',  3500, TRUE),
    ('sample-01', '명란바게트',    '명란 크림을 올린 바게트',             5800, TRUE),
    ('sample-01', '우유식빵',      NULL,                                  5000, FALSE),
    ('sample-02', '숙성 식빵',     '48시간 저온 숙성',                    6500, TRUE),
    ('sample-02', '호두 단팥빵',   NULL,                                  3200, FALSE),
    ('sample-03', '튀김소보로',    '튀긴 소보로 위에 팥소',               2800, TRUE),
    ('sample-03', '부추빵',        NULL,                                  3000, TRUE),
    ('sample-03', '크림치즈 롤',   NULL,                                  3800, FALSE),
    ('sample-04', '앙버터',        '팥과 발효버터를 가득',                4800, TRUE),
    ('sample-04', '무화과 깜빠뉴', NULL,                                  6800, FALSE),
    ('sample-05', '플레인 크루아상', '겹겹이 쌓인 결',                    4200, TRUE),
    ('sample-05', '아몬드 크루아상', NULL,                                5200, TRUE),
    ('sample-06', '쌀 식빵',       '쌀가루 100%',                         6000, TRUE),
    ('sample-07', '마늘빵',        '진한 마늘 소스',                      4500, TRUE),
    ('sample-07', '크림치즈 마늘빵', NULL,                                5200, TRUE),
    ('sample-07', '단호박 스콘',   NULL,                                  3600, FALSE),
    ('sample-08', '플레인 베이글', '쫄깃한 식감',                         3000, TRUE),
    ('sample-08', '어니언 베이글', NULL,                                  3500, FALSE),
    ('sample-09', '단팥빵',        '직접 쑨 팥앙금',                      2500, TRUE),
    ('sample-10', '통밀 깜빠뉴',   '사워도우 발효종 사용',                7000, TRUE)
) AS v(place_id, bread, descr, price, sig)
JOIN bakery b ON b.kakao_place_id = v.place_id
ON CONFLICT (bakery_id, name) DO NOTHING;

INSERT INTO source_link (bakery_id, platform, url, title, summary, author_name, published_at)
SELECT b.id, v.platform, v.url, v.title, v.summary, v.author, now() - (v.days_ago || ' days')::interval
FROM (VALUES
    ('sample-01', 'NAVER_BLOG', 'https://example.com/sample/blog/1', '대전역 소금빵 후기 (샘플)', '샘플 요약입니다.', '샘플블로거', 3),
    ('sample-01', 'INSTAGRAM',  'https://example.com/sample/insta/1', '소금빵 신메뉴 (샘플)',     NULL,              'sample_bakery_01', 1),
    ('sample-03', 'NAVER_BLOG', 'https://example.com/sample/blog/3', '은행동 튀김소보로 줄서기 (샘플)', '샘플 요약입니다.', '샘플블로거', 7),
    ('sample-05', 'NAVER_BLOG', 'https://example.com/sample/blog/5', '둔산 크루아상 맛집 (샘플)', '샘플 요약입니다.', '샘플블로거', 12),
    ('sample-07', 'INSTAGRAM',  'https://example.com/sample/insta/7', '궁동 마늘빵 (샘플)',       NULL,              'sample_bakery_07', 2)
) AS v(place_id, platform, url, title, summary, author, days_ago)
JOIN bakery b ON b.kakao_place_id = v.place_id
ON CONFLICT (bakery_id, url) DO NOTHING;
