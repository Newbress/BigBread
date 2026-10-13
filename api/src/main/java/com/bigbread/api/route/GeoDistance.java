package com.bigbread.api.route;

/** 두 좌표 사이의 직선(대원) 거리. */
public final class GeoDistance {

    /** 지구 평균 반지름(m) */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoDistance() {
    }

    /** Haversine 공식으로 계산한 두 지점 사이의 거리(m). */
    public static double meters(Point a, Point b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng() - a.lng());

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
