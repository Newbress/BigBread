package com.bigbread.api.auth;

/** 화면에 내려주는 로그인 사용자 정보. */
public record UserView(long id, String nickname, String email, String profileImageUrl, String role) {
}
