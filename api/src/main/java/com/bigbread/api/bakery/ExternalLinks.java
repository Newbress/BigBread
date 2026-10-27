package com.bigbread.api.bakery;

import org.springframework.web.util.UriUtils;

/** 외부 지도 서비스로 이동하는 링크. 해당 서비스의 공개 주소 형식만 쓰고 데이터를 가져오지 않는다. */
final class ExternalLinks {

    private ExternalLinks() {
    }

    /**
     * 네이버 지도 검색 링크. 이름에 주소를 붙여 같은 이름의 다른 가게와 헷갈리지 않게 한다.
     * 이름·주소의 특수문자는 경로 한 칸으로 인코딩해서 링크 구조를 깨지 않는다.
     */
    static String naverMapSearch(String name, String roadAddress, String jibunAddress) {
        String address = roadAddress != null && !roadAddress.isBlank() ? roadAddress : jibunAddress;
        String query = address == null || address.isBlank() ? name : name + " " + address;
        return "https://map.naver.com/p/search/" + UriUtils.encodePathSegment(query, java.nio.charset.StandardCharsets.UTF_8);
    }
}
