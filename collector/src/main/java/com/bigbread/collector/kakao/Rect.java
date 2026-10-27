package com.bigbread.collector.kakao;

import java.util.List;
import java.util.Locale;

/** 위경도 사각형. 카카오 장소 검색의 {@code rect} 파라미터(좌, 하, 우, 상 = minLng,minLat,maxLng,maxLat) 순서와 같다. */
public record Rect(double minLng, double minLat, double maxLng, double maxLat) {

    public double width() {
        return maxLng - minLng;
    }

    public double height() {
        return maxLat - minLat;
    }

    /** 카카오 API에 넘기는 문자열: "minLng,minLat,maxLng,maxLat" (소수 6자리, 로케일 무관). */
    public String toParam() {
        return String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f", minLng, minLat, maxLng, maxLat);
    }

    /** 가로·세로를 반씩 나눈 네 구역. */
    public List<Rect> splitInFour() {
        double midLng = (minLng + maxLng) / 2;
        double midLat = (minLat + maxLat) / 2;
        return List.of(
                new Rect(minLng, minLat, midLng, midLat),
                new Rect(midLng, minLat, maxLng, midLat),
                new Rect(minLng, midLat, midLng, maxLat),
                new Rect(midLng, midLat, maxLng, maxLat));
    }

    /** 이 사각형을 한 변이 cell도 이하인 격자 구역으로 나눈다. */
    public List<Rect> grid(double cell) {
        java.util.ArrayList<Rect> cells = new java.util.ArrayList<>();
        for (double lat = minLat; lat < maxLat; lat += cell) {
            for (double lng = minLng; lng < maxLng; lng += cell) {
                cells.add(new Rect(lng, lat, Math.min(lng + cell, maxLng), Math.min(lat + cell, maxLat)));
            }
        }
        return cells;
    }
}
