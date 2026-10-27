package com.bigbread.collector;

import com.bigbread.collector.kakao.KakaoPlace;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 수집한 빵집을 카카오 장소 ID 기준으로 저장한다 (없으면 추가, 있으면 갱신).
 * 갱신하는 것은 카카오가 정하는 기본 정보(이름·주소·좌표·전화)뿐이다.
 * 운영에서 정한 값(상태: 숨김·폐업, 인기 점수, 영업시간, 인스타 계정 등)은 건드리지 않는다.
 */
@Component
public class BakeryUpserter {

    public enum Outcome { INSERTED, UPDATED }

    private final JdbcClient jdbc;

    public BakeryUpserter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Outcome upsert(KakaoPlace place, short districtId) {
        Boolean inserted = jdbc.sql("""
                        INSERT INTO bakery (name, district_id, road_address, jibun_address, location, phone,
                                            kakao_place_id, status, last_synced_at)
                        VALUES (:name, :districtId, :road, :jibun, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), :phone,
                                :kakaoId, 'ACTIVE', now())
                        ON CONFLICT (kakao_place_id) DO UPDATE SET
                            name = EXCLUDED.name,
                            district_id = EXCLUDED.district_id,
                            road_address = EXCLUDED.road_address,
                            jibun_address = EXCLUDED.jibun_address,
                            location = EXCLUDED.location,
                            phone = EXCLUDED.phone,
                            last_synced_at = now(),
                            updated_at = now()
                        RETURNING (xmax = 0) AS inserted
                        """)
                .param("name", cut(place.name(), 100))
                .param("districtId", districtId)
                .param("road", blankToNull(cut(place.roadAddress(), 255)))
                .param("jibun", blankToNull(cut(place.address(), 255)))
                .param("lng", place.lng())
                .param("lat", place.lat())
                .param("phone", blankToNull(cut(place.phone(), 30)))
                .param("kakaoId", cut(place.id(), 30))
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(inserted) ? Outcome.INSERTED : Outcome.UPDATED;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
