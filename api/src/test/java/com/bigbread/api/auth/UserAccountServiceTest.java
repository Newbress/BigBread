package com.bigbread.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostGIS DB가 필요하다. 각 테스트는 롤백된다. */
@SpringBootTest
@Transactional
class UserAccountServiceTest {

    @Autowired
    UserAccountService service;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 처음_로그인하면_사용자와_연동_계정이_만들어진다() {
        long id = service.loginOrRegister(OAuthProvider.KAKAO,
                new OAuthProfile("k-1", "빵순이", "a@example.com", "https://img/1.png"));

        UserView user = service.find(id).orElseThrow();
        assertThat(user.nickname()).isEqualTo("빵순이");
        assertThat(user.email()).isEqualTo("a@example.com");
        assertThat(user.profileImageUrl()).isEqualTo("https://img/1.png");
        assertThat(user.role()).isEqualTo("USER");
        assertThat(count("SELECT count(*) FROM oauth_account WHERE user_id = " + id)).isEqualTo(1);
    }

    @Test
    void 같은_계정으로_다시_로그인하면_같은_사용자이고_닉네임은_유지한다() {
        long first = service.loginOrRegister(OAuthProvider.NAVER, new OAuthProfile("n-1", "처음", null, null));
        jdbc.update("UPDATE app_user SET nickname = '내가바꾼이름' WHERE id = ?", first);

        long second = service.loginOrRegister(OAuthProvider.NAVER,
                new OAuthProfile("n-1", "제공자가준새닉네임", null, "https://img/new.png"));

        assertThat(second).isEqualTo(first);
        UserView user = service.find(first).orElseThrow();
        assertThat(user.nickname()).isEqualTo("내가바꾼이름");
        assertThat(user.profileImageUrl()).isEqualTo("https://img/new.png");
        assertThat(count("SELECT count(*) FROM oauth_account WHERE user_id = " + first)).isEqualTo(1);
    }

    @Test
    void 이메일이_같아도_제공자가_다르면_다른_사용자다() {
        long kakao = service.loginOrRegister(OAuthProvider.KAKAO, new OAuthProfile("same", "A", "same@example.com", null));
        long google = service.loginOrRegister(OAuthProvider.GOOGLE, new OAuthProfile("same", "A", "same@example.com", null));

        assertThat(google).isNotEqualTo(kakao);
    }

    @Test
    void 제공자_ID가_같아도_제공자가_다르면_다른_사용자다() {
        long kakao = service.loginOrRegister(OAuthProvider.KAKAO, new OAuthProfile("100", "A", null, null));
        long naver = service.loginOrRegister(OAuthProvider.NAVER, new OAuthProfile("100", "B", null, null));

        assertThat(naver).isNotEqualTo(kakao);
    }

    @Test
    void 없는_사용자는_빈_값이다() {
        assertThat(service.find(-1)).isEmpty();
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
