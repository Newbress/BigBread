package com.bigbread.api.review.photo;

import java.util.UUID;
import java.util.regex.Pattern;

/** 저장소 파일 이름 규칙. 원본 파일명은 쓰지 않고, 추측할 수 없는 UUID만 쓴다. */
final class PhotoKeys {

    private static final Pattern ID = Pattern.compile("[0-9a-f]{32}");

    private PhotoKeys() {
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    static String main(String id) {
        return id.substring(0, 2) + "/" + id + ".jpg";
    }

    static String thumb(String id) {
        return id.substring(0, 2) + "/" + id + "_t.jpg";
    }
}
