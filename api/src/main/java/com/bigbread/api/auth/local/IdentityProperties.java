package com.bigbread.api.auth.local;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 본인확인 설정 ({@code bigbread.identity.*}).
 *
 * @param provider  none(꺼짐) | fake(개발·시험용, 운영 금지)  — 실제 인증 대행사 연동은 이 값을 늘려 붙인다
 * @param diPepper  연계정보(DI)를 해시할 때 섞는 서버 비밀값 (PASS_DI_PEPPER, 32자 이상). 바꾸면 기존 가입자와 매칭이 끊어지니 바꾸지 말 것
 * @param minAge    가입할 수 있는 최소 만 나이 (개인정보 보호법: 만 14세 미만은 법정대리인 동의가 필요)
 */
@ConfigurationProperties("bigbread.identity")
public record IdentityProperties(String provider, String diPepper, Integer minAge) {

    public IdentityProperties {
        provider = provider == null || provider.isBlank() ? "none" : provider.strip().toLowerCase();
        diPepper = diPepper == null ? "" : diPepper;
        minAge = minAge == null ? 14 : minAge;
    }
}
