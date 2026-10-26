package com.bigbread.api.auth.local;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalAuthRulesTest {

    @Test
    void 아이디는_대소문자를_구분하지_않고_소문자로_통일한다() {
        assertThat(LocalAuthRules.normalizeLoginId("BreadFan01")).isEqualTo("breadfan01");
        assertThat(LocalAuthRules.normalizeLoginId(null)).isNull();
    }

    @Test
    void 올바른_아이디는_통과한다_숫자는_없어도_된다() {
        for (String ok : new String[] {"breadfan", "bread01", "a1234", "abcde", "a".repeat(20), "x" + "1".repeat(19)}) {
            assertThat(LocalAuthRules.loginIdError(ok)).as(ok).isNull();
        }
    }

    @Test
    void 아이디_길이_시작_문자_허용_문자를_검사한다() {
        for (String bad : new String[] {"abcd", "a".repeat(21), "1bread", "_bread", "bread fan", "bread-fan", "bread_fan",
                "빵집사랑이", "bread@fan", "bread.fan", "", "a b c d e"}) {
            assertThat(LocalAuthRules.loginIdError(bad)).as(bad).isNotNull();
        }
        assertThat(LocalAuthRules.loginIdError(null)).isNotNull();
    }

    @Test
    void 예약된_아이디는_거부한다() {
        for (String reserved : new String[] {"admin", "administrator", "bigbread", "support", "webmaster"}) {
            assertThat(LocalAuthRules.loginIdError(reserved)).as(reserved).isEqualTo("사용할 수 없는 아이디예요.");
        }
    }

    @Test
    void 비밀번호는_10자_이상_128자_이하다() {
        assertThat(LocalAuthRules.passwordError("Ab1!Cd2@E", "user")).contains("10~128자"); // 9자
        assertThat(LocalAuthRules.passwordError("Ab1!Cd2@Ef", "user")).isNull(); // 10자
        String max = "Ab1!" + "x".repeat(124); // 128자
        assertThat(max).hasSize(128);
        assertThat(LocalAuthRules.passwordError(max, "user")).isNull();
        assertThat(LocalAuthRules.passwordError(max + "x", "user")).contains("10~128자");
        assertThat(LocalAuthRules.passwordError(null, "user")).isNotNull();
    }

    @Test
    void 영문_숫자_특수문자를_각각_포함해야_한다() {
        String message = "비밀번호에는 영문, 숫자, 특수문자를 각각 1개 이상 넣어 주세요.";
        assertThat(LocalAuthRules.passwordError("abcdefghij1", "u")).isEqualTo(message); // 특수문자 없음
        assertThat(LocalAuthRules.passwordError("abcdefghij!", "u")).isEqualTo(message); // 숫자 없음
        assertThat(LocalAuthRules.passwordError("1234567890!", "u")).isEqualTo(message); // 영문 없음
        assertThat(LocalAuthRules.passwordError("breadLover7!", "u")).isNull();
        assertThat(LocalAuthRules.passwordError("빵을좋아해요12!!", "u")).isNull(); // 한글도 문자로 본다
    }

    @Test
    void 공백만으로는_특수문자로_인정하지_않고_앞뒤_공백을_그대로_둔다() {
        assertThat(LocalAuthRules.passwordError("abcdefg 123", "u")).isNotNull();
        assertThat(LocalAuthRules.passwordError(" abcdefg123!", "u")).isNull();
    }

    @Test
    void 아이디를_포함한_비밀번호는_대소문자와_상관없이_거부한다() {
        assertThat(LocalAuthRules.passwordError("MyBreadFan#2024", "breadfan")).isEqualTo("비밀번호에 아이디를 포함할 수 없어요.");
        assertThat(LocalAuthRules.passwordError("Another#Pass2024", "breadfan")).isNull();
    }

    @Test
    void 흔한_단어에_숫자와_특수문자만_붙인_비밀번호는_거부한다() {
        for (String weak : new String[] {"Password1234!", "qwerty12345!", "iloveyou123!!", "Admin12345678!", "!!Welcome2024"}) {
            assertThat(LocalAuthRules.passwordError(weak, "zzzzzz")).as(weak).isEqualTo("너무 흔한 비밀번호예요. 다른 비밀번호를 써 주세요.");
        }
        assertThat(LocalAuthRules.passwordError("Password-But-Longer-Phrase-9!", "zzzzzz")).isNull();
    }

    @Test
    void 서로_다른_문자가_5개_미만이면_거부한다() {
        assertThat(LocalAuthRules.passwordError("aaaaaaaaa1!", "u")).isEqualTo("서로 다른 문자를 5개 이상 써 주세요.");
        assertThat(LocalAuthRules.passwordError("a1!a1!a1!a1!", "u")).isEqualTo("서로 다른 문자를 5개 이상 써 주세요.");
        assertThat(LocalAuthRules.passwordError("ab1!ab1!ab1!", "u")).isEqualTo("서로 다른 문자를 5개 이상 써 주세요."); // 4종류
        assertThat(LocalAuthRules.passwordError("abc1!abc1!abc", "u")).isNull(); // 5종류
    }

    @Test
    void 이메일은_소문자로_통일하고_형식을_검사한다() {
        assertThat(LocalAuthRules.normalizeEmail("  Bread@Example.COM ")).isEqualTo("bread@example.com");
        assertThat(LocalAuthRules.emailError("bread@example.com")).isNull();
        for (String bad : new String[] {"bread", "bread@", "@example.com", "bread@example", "br ead@example.com", "a@b@c.com"}) {
            assertThat(LocalAuthRules.emailError(bad)).as(bad).isNotNull();
        }
        assertThat(LocalAuthRules.emailError("a".repeat(250) + "@x.com")).isNotNull();
    }

    @Test
    void 닉네임은_2자_이상_20자_이하이고_제어문자를_허용하지_않는다() {
        assertThat(LocalAuthRules.nicknameError("빵")).isNotNull();
        assertThat(LocalAuthRules.nicknameError("빵순이")).isNull();
        assertThat(LocalAuthRules.nicknameError("가".repeat(21))).isNotNull();
        assertThat(LocalAuthRules.nicknameError("  ")).isNotNull();
        assertThat(LocalAuthRules.nicknameError("빵\n순이")).isNotNull();
        assertThat(LocalAuthRules.nicknameError(null)).isNotNull();
    }
}
