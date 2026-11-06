package com.bigbread.api.review.photo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 호환 사진 저장소 설정 ({@code bigbread.photos.s3.*}, {@code bigbread.photos.storage=s3}일 때만 쓴다).
 * AWS S3, Cloudflare R2, Supabase Storage, MinIO처럼 S3 방식으로 말하는 저장소면 어디든 된다.
 *
 * @param endpoint   저장소 주소 (R2 예: https://계정ID.r2.cloudflarestorage.com, AWS S3면 비워도 됨)
 * @param region     리전 (R2는 auto, AWS는 ap-northeast-2 등)
 * @param bucket     버킷 이름 (미리 만들어 두어야 한다. 공개 설정은 필요 없다 — 사진은 API가 대신 내려준다)
 * @param accessKey  접근 키 ID (비밀 값: .env에만 둔다)
 * @param secretKey  비밀 키 (비밀 값: .env에만 둔다)
 * @param pathStyle  true면 주소가 endpoint/버킷/키 형태 (R2·MinIO는 true가 안전)
 * @param keyPrefix  버킷 안에서 사진 파일 이름 앞에 붙일 폴더 (예: photos/)
 */
@ConfigurationProperties("bigbread.photos.s3")
public record S3Properties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        Boolean pathStyle,
        String keyPrefix) {

    public S3Properties {
        region = region == null || region.isBlank() ? "auto" : region;
        pathStyle = pathStyle == null || pathStyle;
        keyPrefix = keyPrefix == null ? "" : keyPrefix;
    }
}
