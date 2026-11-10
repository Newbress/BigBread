package com.bigbread.api.auth.local;

import com.bigbread.api.common.ApiException;

/** 본인확인 제공자가 설정되지 않았을 때. */
class UnavailableIdentityVerifier implements IdentityVerifier {

    @Override
    public String provider() {
        return "none";
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public Result verify(String verificationId) {
        throw ApiException.serviceUnavailable("지금은 본인 인증을 사용할 수 없어요.");
    }
}
