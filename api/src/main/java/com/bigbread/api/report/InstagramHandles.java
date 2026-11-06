package com.bigbread.api.report;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 제보로 받은 인스타그램 계정을 {@code 계정명}으로 정리한다 (@계정, 주소 형태 모두 허용). */
final class InstagramHandles {

    private static final Pattern URL = Pattern.compile("^(?:https?://)?(?:www\\.)?instagram\\.com/([^/?#\\s]+).*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HANDLE = Pattern.compile("[A-Za-z0-9._]{1,30}");

    private InstagramHandles() {
    }

    /** @return 정리한 계정명, 비어 있으면 null. 형식이 잘못됐으면 {@link IllegalArgumentException} */
    static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String value = input.strip();
        Matcher url = URL.matcher(value);
        if (url.matches()) {
            value = url.group(1);
        }
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        if (!HANDLE.matcher(value).matches()) {
            throw new IllegalArgumentException("인스타그램 계정은 영문·숫자·밑줄(_)·점(.)만 쓸 수 있어요.");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
