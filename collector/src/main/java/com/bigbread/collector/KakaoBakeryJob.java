package com.bigbread.collector;

import com.bigbread.collector.kakao.GridSearcher;
import com.bigbread.collector.kakao.KakaoLocalClient;
import com.bigbread.collector.kakao.KakaoPlace;
import com.bigbread.collector.kakao.PlaceSource;
import com.bigbread.collector.kakao.Rect;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 카카오 로컬 API로 대전 5개 구의 빵집을 모두 모아 DB에 저장한다. */
@Component
public class KakaoBakeryJob implements CollectorJob {

    private static final Logger log = LoggerFactory.getLogger(KakaoBakeryJob.class);

    private final CollectorProperties properties;
    private final JdbcClient jdbc;
    private final BakeryUpserter upserter;
    private final Function<CollectorProperties, PlaceSource> sourceFactory;

    @Autowired
    public KakaoBakeryJob(CollectorProperties properties, JdbcClient jdbc, BakeryUpserter upserter) {
        this(properties, jdbc, upserter, KakaoBakeryJob::realSource);
    }

    /** 테스트에서 가짜 검색 소스를 넣을 수 있게 열어 둔 생성자 */
    KakaoBakeryJob(CollectorProperties properties, JdbcClient jdbc, BakeryUpserter upserter,
                   Function<CollectorProperties, PlaceSource> sourceFactory) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.upserter = upserter;
        this.sourceFactory = sourceFactory;
    }

    private static PlaceSource realSource(CollectorProperties properties) {
        String key = properties.kakao().restApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("KAKAO_REST_API_KEY가 비어 있어요. 레포 루트 .env에 카카오 REST API 키를 넣어 주세요.");
        }
        return new KakaoLocalClient(properties.kakao().baseUrl(), key, properties.kakao().requestDelayMs());
    }

    @Override
    public String name() {
        return "kakao-bakeries";
    }

    @Override
    public String description() {
        return "카카오 로컬 API로 대전 5개 구의 빵집을 모두 모아 저장한다 (카카오 장소 ID 기준으로 추가·갱신)";
    }

    @Override
    public void run(boolean dryRun) {
        PlaceSource source = sourceFactory.apply(properties);
        Map<String, Short> districtIds = loadDistrictIds();
        PlaceFilter filter = new PlaceFilter(properties, districtIds);

        CollectorProperties.Area area = properties.area();
        log.info("수집 시작: 영역 lat {}~{}, lng {}~{}, 키워드 {}{}", area.minLat(), area.maxLat(), area.minLng(), area.maxLng(),
                properties.keywords(), dryRun ? " (dry-run: DB에 쓰지 않습니다)" : "");
        GridSearcher.Result found = new GridSearcher(source, area.minCellDegrees()).search(
                new Rect(area.minLng(), area.minLat(), area.maxLng(), area.maxLat()), area.cellDegrees(), properties.keywords());

        Map<PlaceFilter.Decision, Integer> decisions = new EnumMap<>(PlaceFilter.Decision.class);
        Map<String, Integer> perDistrict = new HashMap<>();
        int inserted = 0;
        int updated = 0;
        for (KakaoPlace place : found.places().values()) {
            PlaceFilter.Decision decision = filter.decide(place);
            decisions.merge(decision, 1, Integer::sum);
            if (decision != PlaceFilter.Decision.ACCEPT) {
                continue;
            }
            short districtId = filter.districtOf(place);
            perDistrict.merge(place.address().split("\\s+")[1], 1, Integer::sum);
            if (dryRun) {
                continue;
            }
            if (upserter.upsert(place, districtId) == BakeryUpserter.Outcome.INSERTED) {
                inserted++;
            } else {
                updated++;
            }
        }

        log.info("수집 끝: API {}번 호출, 구역 {}개 검색, 검색 결과 {}곳", source.calls(), found.searchedCells(), found.places().size());
        log.info("  분류: 저장 대상 {} / 빵집 아님 {} / 대전 밖 {} / 제외 이름 {} / 정보 부족 {}",
                decisions.getOrDefault(PlaceFilter.Decision.ACCEPT, 0), decisions.getOrDefault(PlaceFilter.Decision.NOT_BAKERY, 0),
                decisions.getOrDefault(PlaceFilter.Decision.OUTSIDE_AREA, 0), decisions.getOrDefault(PlaceFilter.Decision.EXCLUDED_NAME, 0),
                decisions.getOrDefault(PlaceFilter.Decision.BAD_DATA, 0));
        log.info("  구별: {}", perDistrict);
        if (dryRun) {
            log.info("  (dry-run) 저장하지 않았습니다.");
        } else {
            log.info("  저장: 새로 {}곳 추가, {}곳 갱신", inserted, updated);
        }
        if (found.truncatedCells() > 0) {
            log.warn("  주의: 더 쪼갤 수 없는 구역 {}곳에서 결과가 잘려 일부 빵집이 빠졌을 수 있어요.", found.truncatedCells());
        }
    }

    private Map<String, Short> loadDistrictIds() {
        Map<String, Short> ids = new HashMap<>();
        jdbc.sql("SELECT id, name FROM district").query((rs, n) -> {
            ids.put(rs.getString("name"), rs.getShort("id"));
            return null;
        }).list();
        return ids;
    }
}
