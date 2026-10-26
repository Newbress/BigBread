package com.bigbread.api.auth;

/** 제공자(카카오/네이버/구글)마다 다른 사용자 정보 응답을 하나로 맞춘 값. */
public record OAuthProfile(String providerUserId, String nickname, String email, String profileImageUrl) {
}
