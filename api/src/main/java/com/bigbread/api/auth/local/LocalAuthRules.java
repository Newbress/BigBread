package com.bigbread.api.auth.local;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 자체 가입 입력 규칙. 오류가 있으면 사용자에게 그대로 보여줄 수 있는 한국어 메시지를 돌려주고, 없으면 null이다.
 *
 * <ul>
 *   <li>아이디: 영문으로 시작, 영문·숫자, 5~20자, 대소문자 구분 없음(소문자로 통일). 숫자는 필수가 아니다.</li>
 *   <li>비밀번호: 10~128자, 영문·숫자·특수문자를 각각 1개 이상, 서로 다른 문자 5개 이상.
 *       아이디를 포함하거나 흔한 단어뿐이면 거부.</li>
 * </ul>
 */
public final class LocalAuthRules {

    public static final int PASSWORD_MIN = 10;
    public static final int PASSWORD_MAX = 128;
    private static final int MIN_DISTINCT_CHARS = 5;

    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z][a-z0-9]{4,19}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int EMAIL_MAX = 255;
    private static final int NICKNAME_MIN = 2;
    private static final int NICKNAME_MAX = 20;

    private static final Set<String> RESERVED_LOGIN_IDS = Set.of(
            "admin", "administrator", "root", "system", "support", "bigbread", "master", "manager",
            "service", "staff", "operator", "official", "webmaster", "postmaster", "anonymous", "guest");

    private static final Set<String> COMMON_WORDS = loadCommonWords();

    private LocalAuthRules() {
    }

    /** 아이디를 소문자로 통일한다. null이면 null. */
    public static String normalizeLoginId(String raw) {
        return raw == null ? null : raw.toLowerCase(Locale.ROOT);
    }

    public static String normalizeEmail(String raw) {
        return raw == null ? null : raw.strip().toLowerCase(Locale.ROOT);
    }

    /** @param normalizedLoginId {@link #normalizeLoginId}를 거친 값 */
    public static String loginIdError(String normalizedLoginId) {
        if (normalizedLoginId == null || !LOGIN_ID.matcher(normalizedLoginId).matches()) {
            return "아이디는 영문으로 시작하는 영문·숫자 5~20자로 입력해 주세요.";
        }
        if (RESERVED_LOGIN_IDS.contains(normalizedLoginId)) {
            return "사용할 수 없는 아이디예요.";
        }
        return null;
    }

    public static String emailError(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.length() > EMAIL_MAX || !EMAIL.matcher(normalizedEmail).matches()) {
            return "이메일 형식이 올바르지 않아요.";
        }
        return null;
    }

    public static String nicknameError(String nickname) {
        if (nickname == null) {
            return "닉네임을 입력해 주세요.";
        }
        String trimmed = nickname.strip();
        if (trimmed.length() < NICKNAME_MIN || trimmed.length() > NICKNAME_MAX
                || trimmed.chars().anyMatch(Character::isISOControl)) {
            return "닉네임은 2~20자로 입력해 주세요.";
        }
        return null;
    }

    /** @param normalizedLoginId 비밀번호에 아이디가 들어 있는지 볼 때 쓴다. 없으면 null. */
    public static String passwordError(String password, String normalizedLoginId) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            return "비밀번호는 " + PASSWORD_MIN + "~" + PASSWORD_MAX + "자로 입력해 주세요.";
        }
        boolean letter = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); ) {
            int cp = password.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) {
                letter = true;
            } else if (Character.isDigit(cp)) {
                digit = true;
            } else if (!Character.isWhitespace(cp) && !Character.isISOControl(cp)) {
                special = true;
            }
        }
        if (!letter || !digit || !special) {
            return "비밀번호에는 영문, 숫자, 특수문자를 각각 1개 이상 넣어 주세요.";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (normalizedLoginId != null && !normalizedLoginId.isEmpty() && lower.contains(normalizedLoginId)) {
            return "비밀번호에 아이디를 포함할 수 없어요.";
        }
        String lettersOnly = lower.replaceAll("[^a-z]", "");
        if (COMMON_WORDS.contains(lettersOnly)) {
            return "너무 흔한 비밀번호예요. 다른 비밀번호를 써 주세요.";
        }
        // 영문·숫자·특수문자를 하나씩만 반복한 값(예: aaaaaaaaa1!)을 막는다.
        if (password.codePoints().distinct().count() < MIN_DISTINCT_CHARS) {
            return "서로 다른 문자를 " + MIN_DISTINCT_CHARS + "개 이상 써 주세요.";
        }
        return null;
    }

    private static Set<String> loadCommonWords() {
        try (InputStream in = LocalAuthRules.class.getResourceAsStream("/auth/common-password-words.txt")) {
            if (in == null) {
                throw new IllegalStateException("auth/common-password-words.txt 를 찾을 수 없습니다.");
            }
            Set<String> words = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.strip();
                    if (!word.isEmpty() && !word.startsWith("#")) {
                        words.add(word.toLowerCase(Locale.ROOT));
                    }
                }
            }
            return Set.copyOf(words);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
