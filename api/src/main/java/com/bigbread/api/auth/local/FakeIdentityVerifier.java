package com.bigbread.api.auth.local;

import com.bigbread.api.common.ApiException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 개발·테스트용 가짜 본인확인. 인증 ID 를 {@code fake:<연계정보>:<생년월일 yyyy-MM-dd>[:<구분용 값>]} 로 만들어 보내면 그대로 통과시킨다.
 * 마지막 구분용 값은 인증 ID 를 매번 다르게 만들 때만 쓴다 (실제 인증은 인증마다 ID 가 다르다).
 * 운영에서는 켜지지 않는다 (ProductionChecks 가 시작을 막는다).
 */
class FakeIdentityVerifier implements IdentityVerifier {

    @Override
    public String provider() {
        return "fake";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Result verify(String verificationId) {
        String[] parts = verificationId == null ? new String[0] : verificationId.split(":", 4);
        if (parts.length < 3 || !"fake".equals(parts[0]) || parts[1].isBlank()) {
            throw ApiException.badRequest("본인 인증이 완료되지 않았어요. 다시 시도해 주세요.");
        }
        try {
            return new Result(parts[1], LocalDate.parse(parts[2]));
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("본인 인증이 완료되지 않았어요. 다시 시도해 주세요.");
        }
    }
}
