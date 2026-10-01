package com.bigbread.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bigbread.core.Bakery;
import com.bigbread.core.BakeryRepository;
import com.bigbread.core.DistrictRepository;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 PostgreSQL/PostGIS가 필요하다. 기본값은 docker-compose의 DB(localhost:5432)이며
 * DB_URL / DB_USERNAME / DB_PASSWORD 환경변수로 바꿀 수 있다 (CI는 서비스 컨테이너 사용).
 */
@SpringBootTest
@Transactional
class SchemaIntegrationTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    DistrictRepository districtRepository;

    @Autowired
    BakeryRepository bakeryRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 대전_5개_구가_시드된다() {
        assertThat(districtRepository.findAll())
                .extracting("name")
                .containsExactlyInAnyOrder("동구", "중구", "서구", "유성구", "대덕구");
    }

    @Test
    void 빵집_저장_후_반경_검색이_동작한다() {
        var seo = districtRepository.findById((short) 3).orElseThrow();
        // 성심당 본점 부근(중구) / 둔산동(서구) 좌표
        Point near = GF.createPoint(new Coordinate(127.4274, 36.3277));
        Point far = GF.createPoint(new Coordinate(127.3880, 36.3504));
        var a = bakeryRepository.save(new Bakery("테스트빵집A", seo, near));
        var b = bakeryRepository.save(new Bakery("테스트빵집B", seo, far));

        Integer within1km = jdbc.queryForObject("""
                SELECT count(*) FROM bakery
                WHERE id IN (?, ?)
                  AND ST_DWithin(location::geography,
                                 ST_SetSRID(ST_MakePoint(127.4274, 36.3277), 4326)::geography, 1000)
                """, Integer.class, a.getId(), b.getId());

        assertThat(within1km).isEqualTo(1);
    }
}
