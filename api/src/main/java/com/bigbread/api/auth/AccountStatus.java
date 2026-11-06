package com.bigbread.api.auth;

import com.bigbread.api.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 정지된 계정은 글(리뷰·사진·제보·신고)을 쓸 수 없다. 로그인과 조회는 그대로 된다. */
@Component
public class AccountStatus {

    private final JdbcClient jdbc;

    public AccountStatus(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void requireActive(long userId) {
        jdbc.sql("SELECT suspended_reason FROM app_user WHERE id = :id AND suspended_at IS NOT NULL")
                .param("id", userId)
                .query(String.class)
                .optional()
                .ifPresent(reason -> {
                    throw ApiException.forbidden("정지된 계정이라 글을 쓸 수 없어요." + (reason == null || reason.isBlank() ? "" : " (사유: " + reason + ")"));
                });
    }
}
