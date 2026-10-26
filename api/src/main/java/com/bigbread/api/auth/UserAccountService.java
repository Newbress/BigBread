package com.bigbread.api.auth;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 계정과 서비스 사용자(app_user)를 연결한다.
 * 같은 이메일이어도 제공자가 다르면 자동으로 합치지 않는다 (제공자가 돌려주는 이메일을 믿고 합치면 계정 탈취에 쓰일 수 있다).
 */
@Service
public class UserAccountService {

    private final JdbcClient jdbc;

    public UserAccountService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 처음 로그인이면 사용자를 만들고, 아니면 기존 사용자를 돌려준다. 사용자 ID를 반환한다. */
    @Transactional
    public long loginOrRegister(OAuthProvider provider, OAuthProfile profile) {
        Optional<Long> existing = findUserId(provider, profile.providerUserId());
        if (existing.isPresent()) {
            long userId = existing.get();
            // 닉네임은 사용자가 바꿀 수 있어서 덮어쓰지 않고, 프로필 사진만 최신으로 맞춘다.
            jdbc.sql("UPDATE app_user SET profile_image_url = COALESCE(:image, profile_image_url), updated_at = now() "
                            + "WHERE id = :id")
                    .param("image", profile.profileImageUrl())
                    .param("id", userId)
                    .update();
            return userId;
        }

        long userId = jdbc.sql("""
                        INSERT INTO app_user (email, nickname, profile_image_url)
                        VALUES (:email, :nickname, :image) RETURNING id
                        """)
                .param("email", profile.email())
                .param("nickname", profile.nickname())
                .param("image", profile.profileImageUrl())
                .query(Long.class)
                .single();
        int linked = jdbc.sql("""
                        INSERT INTO oauth_account (user_id, provider, provider_user_id)
                        VALUES (:userId, :provider, :providerUserId)
                        ON CONFLICT (provider, provider_user_id) DO NOTHING
                        """)
                .param("userId", userId)
                .param("provider", provider.name())
                .param("providerUserId", profile.providerUserId())
                .update();
        if (linked == 0) {
            // 같은 계정으로 동시에 처음 로그인한 다른 요청이 먼저 만들었다: 방금 만든 사용자는 지우고 그쪽을 쓴다.
            jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", userId).update();
            return findUserId(provider, profile.providerUserId()).orElseThrow();
        }
        return userId;
    }

    public Optional<UserView> find(long userId) {
        return jdbc.sql("SELECT id, nickname, email, profile_image_url, role FROM app_user WHERE id = :id")
                .param("id", userId)
                .query((rs, n) -> new UserView(rs.getLong("id"), rs.getString("nickname"), rs.getString("email"),
                        rs.getString("profile_image_url"), rs.getString("role")))
                .optional();
    }

    private Optional<Long> findUserId(OAuthProvider provider, String providerUserId) {
        return jdbc.sql("SELECT user_id FROM oauth_account WHERE provider = :provider AND provider_user_id = :id")
                .param("provider", provider.name())
                .param("id", providerUserId)
                .query(Long.class)
                .optional();
    }
}
