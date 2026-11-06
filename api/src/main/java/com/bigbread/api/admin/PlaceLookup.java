package com.bigbread.api.admin;

import java.util.Optional;

/** 이름·주소로 빵집의 위치와 카카오 장소 정보를 찾는다 (새 빵집 제보를 승인할 때 좌표를 채우는 용도). */
public interface PlaceLookup {

    record Place(String kakaoPlaceId, String name, String roadAddress, String jibunAddress, double lat, double lng,
                 String phone) {
    }

    Optional<Place> find(String name, String address);
}
