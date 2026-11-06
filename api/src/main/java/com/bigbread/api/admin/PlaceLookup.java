package com.bigbread.api.admin;

import java.util.List;
import java.util.Optional;

/** 이름·주소로 빵집의 위치와 카카오 장소 정보를 찾는다 (새 빵집 제보를 승인할 때 좌표를 채우는 용도). */
public interface PlaceLookup {

    record Place(String kakaoPlaceId, String name, String roadAddress, String jibunAddress, double lat, double lng,
                 String phone) {
    }

    Optional<Place> find(String name, String address);

    /** 운영자가 직접 고르도록 후보를 보여줄 때 쓴다. kakaoPlaceId가 없는 후보는 주소만으로 찾은 위치다. */
    record Candidate(Place place, String category) {
    }

    /** 키워드(이름·주소)로 찾은 후보. 카카오 장소가 없으면 주소 검색 결과를 준다. 못 찾으면 빈 목록. */
    default List<Candidate> search(String query) {
        return List.of();
    }
}
