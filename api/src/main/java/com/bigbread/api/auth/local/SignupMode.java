package com.bigbread.api.auth.local;

/**
 * 자체 가입 방식 ({@code bigbread.auth.signup-mode}, 환경 변수 AUTH_SIGNUP_MODE).
 *
 * <ul>
 *   <li>{@code EMAIL}: 이메일을 받고, 메일의 링크로 인증해야 계정이 만들어진다 (메일 설정 필요)</li>
 *   <li>{@code INSTANT}: 이메일을 받지만 인증은 하지 않고 바로 가입·로그인한다. 이메일은 비밀번호 재설정·아이디 찾기에만 쓴다 (메일 설정 필요)</li>
 *   <li>{@code OPEN}: 이메일을 받지 않고 바로 가입한다. 메일 복구가 없어 비밀번호는 운영자가 임시 비밀번호로 초기화해 준다</li>
 * </ul>
 */
enum SignupMode {
    EMAIL, INSTANT, OPEN;

    static SignupMode parse(String value) {
        String text = value == null ? "" : value.strip();
        for (SignupMode mode : values()) {
            if (mode.name().equalsIgnoreCase(text)) {
                return mode;
            }
        }
        return EMAIL;
    }

    /** 가입할 때 이메일을 받는가 */
    boolean collectsEmail() {
        return this != OPEN;
    }

    /** 이메일 인증 링크가 필요한가 */
    boolean verifiesEmail() {
        return this == EMAIL;
    }

    /** 인증 대기 없이 바로 계정을 만드는가 */
    boolean direct() {
        return this != EMAIL;
    }
}
