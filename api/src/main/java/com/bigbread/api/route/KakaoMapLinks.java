package com.bigbread.api.route;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.web.util.UriUtils;

/** 카카오맵 길찾기 링크 생성. 링크는 나중에 제외될 수 있어 서비스 로직과 분리해 둔다. */
public final class KakaoMapLinks {

    private static final String WALK_URL = "https://map.kakao.com/link/by/walk/";

    private KakaoMapLinks() {
    }

    /** 출발지 → 도착지 도보 길찾기 링크: {@code .../link/by/walk/{이름},{위도},{경도}/{이름},{위도},{경도}} */
    public static String walk(String fromName, Point from, String toName, Point to) {
        return WALK_URL + place(fromName, from) + "/" + place(toName, to);
    }

    private static String place(String name, Point point) {
        // 쉼표와 슬래시는 구분자라서, URL로 의미를 갖는 문자는 공백으로 바꾼 뒤 인코딩한다.
        String safe = name.replaceAll("[,/?#%]", " ").trim();
        return UriUtils.encodePathSegment(safe, StandardCharsets.UTF_8)
                + "," + String.format(Locale.ROOT, "%.6f", point.lat())
                + "," + String.format(Locale.ROOT, "%.6f", point.lng());
    }
}
