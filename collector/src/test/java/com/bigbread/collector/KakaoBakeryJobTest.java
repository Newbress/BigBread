package com.bigbread.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bigbread.collector.kakao.KakaoPlace;
import com.bigbread.collector.kakao.PlacePage;
import com.bigbread.collector.kakao.PlaceSource;
import com.bigbread.collector.kakao.Rect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostGIS DB가 필요하다 (스키마는 테스트가 Flyway로 만든다). 각 테스트는 롤백된다. */
// dev 프로필의 샘플 데이터(R__) 이력이 남은 DB를 열어도 검증이 실패하지 않게 api 모듈과 같은 옵션을 쓴다.
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.flyway.ignore-migration-patterns=repeatable:missing"})
@Transactional
class KakaoBakeryJobTest {

    private static final String CATEGORY = "음식점 > 간식 > 제과,베이커리";

    @Autowired
    JdbcClient jdbc;

    @Autowired
    BakeryUpserter upserter;

    /** 모든 질의에 같은 결과를 한 페이지로 돌려주는 가짜 소스 */
    private static PlaceSource fixed(List<KakaoPlace> places) {
        return new PlaceSource() {
            @Override
            public PlacePage search(String query, Rect rect, int page) {
                // 영역 안에 있는 것만 돌려준다 (격자 구역마다 같은 장소가 중복으로 나오지 않게)
                List<KakaoPlace> inside = places.stream()
                        .filter(p -> p.lng() >= rect.minLng() && p.lng() <= rect.maxLng() && p.lat() >= rect.minLat() && p.lat() <= rect.maxLat())
                        .toList();
                return new PlacePage(inside, inside.size(), inside.size(), true);
            }
        };
    }

    private static KakaoPlace place(String id, String name, String address, double lng, double lat) {
        return new KakaoPlace(id, name, CATEGORY, "042-111-" + id, address, "대전 도로명 " + id, lng, lat, "http://place.map.kakao.com/" + id);
    }

    private KakaoBakeryJob job(List<KakaoPlace> places, CollectorProperties properties) {
        return new KakaoBakeryJob(properties, jdbc, upserter, p -> fixed(places));
    }

    private static CollectorProperties props() {
        return new CollectorProperties("", false, null, null, null, null, null);
    }

    private long count(String where) {
        return jdbc.sql("SELECT count(*) FROM bakery WHERE " + where).query(Long.class).single();
    }

    @Test
    void 대전의_빵집만_저장하고_프랜차이즈_대전_밖_빵집_아님은_거른다() {
        List<KakaoPlace> places = List.of(
                place("9001", "테스트빵집A", "대전 유성구 궁동 1", 127.35, 36.36),
                place("9002", "테스트빵집B", "대전 중구 은행동 2", 127.43, 36.33),
                place("9003", "파리바게뜨 테스트점", "대전 서구 둔산동 3", 127.38, 36.35),
                place("9004", "세종빵집", "세종특별자치시 조치원읍 4", 127.30, 36.60),
                new KakaoPlace("9005", "테스트카페", "음식점 > 카페 > 커피전문점", "", "대전 동구 중앙동 5", "", 127.44, 36.33, ""));

        job(places, props()).run(false);

        assertThat(count("kakao_place_id IN ('9001','9002')")).isEqualTo(2);
        assertThat(count("kakao_place_id IN ('9003','9004','9005')")).isZero();
        assertThat(jdbc.sql("SELECT d.name FROM bakery b JOIN district d ON d.id = b.district_id WHERE b.kakao_place_id = '9001'")
                .query(String.class).single()).isEqualTo("유성구");
    }

    @Test
    void 저장_내용에_이름_주소_좌표_전화_상태_동기화_시각이_들어간다() {
        job(List.of(place("9101", "테스트빵집", "대전 대덕구 송촌동 1", 127.43, 36.37)), props()).run(false);

        var row = jdbc.sql("""
                SELECT name, road_address, jibun_address, phone, status, last_synced_at IS NOT NULL AS synced,
                       ST_X(location) AS lng, ST_Y(location) AS lat
                  FROM bakery WHERE kakao_place_id = '9101'
                """).query((rs, n) -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getBoolean(6), rs.getDouble(7), rs.getDouble(8)}).single();
        assertThat(row).containsExactly("테스트빵집", "대전 도로명 9101", "대전 대덕구 송촌동 1", "042-111-9101", "ACTIVE", true, 127.43, 36.37);
    }

    @Test
    void 다시_수집하면_중복으로_쌓이지_않고_바뀐_정보만_갱신한다() {
        job(List.of(place("9201", "옛이름", "대전 서구 둔산동 1", 127.38, 36.35)), props()).run(false);
        job(List.of(place("9201", "새이름", "대전 서구 둔산동 1", 127.38, 36.35)), props()).run(false);

        assertThat(count("kakao_place_id = '9201'")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT name FROM bakery WHERE kakao_place_id = '9201'").query(String.class).single()).isEqualTo("새이름");
    }

    @Test
    void 운영에서_정한_값은_다시_수집해도_덮어쓰지_않는다() {
        job(List.of(place("9301", "테스트빵집", "대전 서구 둔산동 1", 127.38, 36.35)), props()).run(false);
        jdbc.sql("""
                UPDATE bakery SET status = 'HIDDEN', popularity_score = 77, instagram_username = 'my_bakery',
                                  opening_hours = '{"mon":"09:00-18:00"}'::jsonb WHERE kakao_place_id = '9301'
                """).update();

        job(List.of(place("9301", "테스트빵집", "대전 서구 둔산동 1", 127.38, 36.35)), props()).run(false);

        var row = jdbc.sql("SELECT status, popularity_score, instagram_username, opening_hours::text FROM bakery WHERE kakao_place_id = '9301'")
                .query((rs, n) -> new Object[] {rs.getString(1), rs.getDouble(2), rs.getString(3), rs.getString(4)}).single();
        assertThat(row[0]).isEqualTo("HIDDEN");
        assertThat(row[1]).isEqualTo(77.0);
        assertThat(row[2]).isEqualTo("my_bakery");
        assertThat((String) row[3]).contains("09:00-18:00");
    }

    @Test
    void dry_run이면_DB에_아무것도_쓰지_않는다() {
        job(List.of(place("9401", "테스트빵집", "대전 서구 둔산동 1", 127.38, 36.35)), props()).run(true);

        assertThat(count("kakao_place_id = '9401'")).isZero();
    }

    @Test
    void 전화번호가_비어_있으면_null로_저장한다() {
        KakaoPlace noPhone = new KakaoPlace("9501", "전화없음", CATEGORY, "", "대전 서구 둔산동 1", "", 127.38, 36.35, "");
        job(List.of(noPhone), props()).run(false);

        assertThat(jdbc.sql("SELECT phone IS NULL FROM bakery WHERE kakao_place_id = '9501'").query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT road_address IS NULL FROM bakery WHERE kakao_place_id = '9501'").query(Boolean.class).single()).isTrue();
    }

    @Test
    void 키가_없으면_분명한_메시지로_실패한다() {
        KakaoBakeryJob real = new KakaoBakeryJob(props(), jdbc, upserter); // 실제 소스: 키 없음
        assertThatThrownBy(() -> real.run(false)).isInstanceOf(IllegalStateException.class).hasMessageContaining("KAKAO_REST_API_KEY");
    }

    @Test
    void 러너는_작업_이름이_없으면_사용법만_출력하고_모르는_이름은_거부한다() {
        List<CollectorJob> jobs = new ArrayList<>();
        jobs.add(job(List.of(), props()));

        new CollectorRunner(props(), jobs).run(new DefaultApplicationArguments());

        CollectorProperties unknown = new CollectorProperties("nope", false, null, null, null, null, null);
        assertThatThrownBy(() -> new CollectorRunner(unknown, jobs).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nope").hasMessageContaining("kakao-bakeries");

        CollectorProperties named = new CollectorProperties("kakao-bakeries", true, null, null, null, null, null);
        new CollectorRunner(named, jobs).run(new DefaultApplicationArguments()); // dry-run으로 정상 실행
    }
}
