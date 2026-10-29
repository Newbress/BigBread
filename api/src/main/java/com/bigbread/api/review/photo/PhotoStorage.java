package com.bigbread.api.review.photo;

import java.util.Optional;

/**
 * 사진 파일 저장소. 지금은 로컬 폴더 구현만 있고, 운영에서는 S3 호환 저장소 구현으로 바꾼다.
 * 키는 {@link PhotoKeys}가 만든 이름만 쓴다 (경로 조작을 막기 위해 구현이 다시 검사한다).
 */
public interface PhotoStorage {

    void put(String key, byte[] data);

    Optional<byte[]> read(String key);

    /** 없어도 오류 없이 끝난다. */
    void delete(String key);
}
