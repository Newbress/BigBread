package com.bigbread.api.review.photo;

/** HEIC/HEIF(아이폰 기본 사진 형식)를 JPEG 바이트로 바꾼다. */
public interface HeifConverter {

    /** @throws PhotoRejectedException 변환할 수 없을 때 (사용자에게 보여줄 안내 문구 포함) */
    byte[] toJpeg(byte[] heic);
}
