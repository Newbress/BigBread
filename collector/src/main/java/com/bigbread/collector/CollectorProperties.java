package com.bigbread.collector;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 수집기 설정. 실행할 작업은 {@code collector.job}으로 고른다 (예: {@code --collector.job=kakao-bakeries}).
 *
 * @param job           실행할 작업 이름. 비어 있으면 사용법만 출력하고 끝난다.
 * @param dryRun        true면 DB에 쓰지 않고 수집 결과 요약만 출력한다.
 * @param keywords      카카오 장소 검색 키워드 (여러 개를 각각 검색해 합친다)
 * @param categoryKeywords 카테고리 이름에 이 중 하나가 들어 있어야 빵집으로 본다
 * @param excludedNames 이름에 이 중 하나가 들어 있으면 제외한다 (대형 프랜차이즈 등, 공백 무시·대소문자 무시)
 */
@ConfigurationProperties("collector")
public record CollectorProperties(
        String job,
        boolean dryRun,
        Kakao kakao,
        Area area,
        List<String> keywords,
        List<String> categoryKeywords,
        List<String> excludedNames) {

    /**
     * @param restApiKey     카카오 REST API 키 (.env의 KAKAO_REST_API_KEY)
     * @param baseUrl        카카오 로컬 API 주소 (테스트에서 바꾼다)
     * @param requestDelayMs 호출 사이 대기(ms). 카카오 호출 한도를 넘지 않게 한다.
     */
    public record Kakao(String restApiKey, String baseUrl, long requestDelayMs) {
        public Kakao {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://dapi.kakao.com" : baseUrl;
            requestDelayMs = requestDelayMs <= 0 ? 60 : requestDelayMs;
        }
    }

    /**
     * 검색할 영역(위경도 사각형). 기본값은 대전 전체를 넉넉히 덮는다. 결과는 주소가 '대전'인 것만 남긴다.
     *
     * @param cellDegrees    처음 나누는 구역 크기(도). 한 번에 45개가 넘게 나오는 구역은 다시 4등분한다.
     * @param minCellDegrees 더 쪼갤 수 있는 최소 구역 크기(도, 약 0.0004도 = 40m)
     */
    public record Area(double minLat, double minLng, double maxLat, double maxLng, double cellDegrees,
                       double minCellDegrees) {
        public Area {
            if (minLat == 0 && minLng == 0 && maxLat == 0 && maxLng == 0) {
                minLat = 36.17;
                minLng = 127.22;
                maxLat = 36.52;
                maxLng = 127.58;
            }
            cellDegrees = cellDegrees <= 0 ? 0.08 : cellDegrees;
            minCellDegrees = minCellDegrees <= 0 ? 0.0004 : minCellDegrees;
        }
    }

    public CollectorProperties {
        kakao = kakao == null ? new Kakao(null, null, 0) : kakao;
        area = area == null ? new Area(0, 0, 0, 0, 0, 0) : area;
        keywords = keywords == null || keywords.isEmpty() ? List.of("빵집", "베이커리", "제과점") : List.copyOf(keywords);
        categoryKeywords = categoryKeywords == null || categoryKeywords.isEmpty()
                ? List.of("제과,베이커리", "베이커리")
                : List.copyOf(categoryKeywords);
        excludedNames = excludedNames == null
                ? List.of("파리바게뜨", "파리바게트", "뚜레쥬르", "던킨", "크리스피크림", "크리스피 크림")
                : List.copyOf(excludedNames);
    }
}
