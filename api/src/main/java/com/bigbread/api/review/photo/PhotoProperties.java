package com.bigbread.api.review.photo;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 리뷰 사진 설정 ({@code bigbread.photos.*}).
 *
 * @param dir          개발용 로컬 저장 폴더 (운영에서는 S3 호환 저장소 구현으로 바꾼다)
 * @param maxBytes     올릴 수 있는 사진 한 장의 최대 크기 (바이트)
 * @param maxPerReview 리뷰 하나에 붙일 수 있는 사진 수
 * @param maxEdge      저장하는 사진의 긴 변 최대 픽셀. 이보다 크면 줄이고 작으면 그대로 둔다
 * @param thumbEdge    목록용 썸네일의 긴 변 픽셀
 * @param quality      JPEG 품질 (0~1)
 * @param maxPixels    읽어 들일 수 있는 최대 해상도(가로 x 세로). 터무니없이 큰 이미지로 서버 메모리를 쓰게 하는 공격을 막는다
 * @param heifCommand  HEIC를 JPEG로 바꾸는 명령. {@code {in}}, {@code {out}}이 파일 경로로 바뀐다. 비우면 HEIC 서버 변환은 꺼진다
 * @param uploadsPerHour 한 사용자가 한 시간에 올릴 수 있는 사진 수
 */
@ConfigurationProperties("bigbread.photos")
public record PhotoProperties(
        Path dir,
        Long maxBytes,
        Integer maxPerReview,
        Integer maxEdge,
        Integer thumbEdge,
        Double quality,
        Long maxPixels,
        String heifCommand,
        Integer uploadsPerHour) {

    public PhotoProperties {
        dir = dir == null ? Path.of("data/uploads") : dir;
        maxBytes = maxBytes == null ? 10L * 1024 * 1024 : maxBytes;
        maxPerReview = maxPerReview == null ? 3 : maxPerReview;
        maxEdge = maxEdge == null ? 2560 : maxEdge;
        thumbEdge = thumbEdge == null ? 480 : thumbEdge;
        quality = quality == null ? 0.90 : quality;
        maxPixels = maxPixels == null ? 80_000_000L : maxPixels;
        heifCommand = heifCommand == null ? "heif-convert -q 92 {in} {out}" : heifCommand;
        uploadsPerHour = uploadsPerHour == null ? 30 : uploadsPerHour;
    }
}
