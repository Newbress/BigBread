package com.bigbread.api.auth.local;

import java.time.LocalDate;

/**
 * 휴대폰 본인확인(PASS 등) 결과를 서버에서 확인한다. 브라우저가 알려 준 "인증을 마쳤다"는 말은 믿지 않고,
 * 인증 대행사에 인증 ID 로 직접 물어 결과를 받아 온다.
 */
public interface IdentityVerifier {

    /** 이름: 화면과 로그에 쓰는 제공자 이름 (fake, none, ...) */
    String provider();

    /** 실제로 인증을 처리할 수 있는가 (설정이 비어 있으면 false) */
    boolean available();

    /**
     * @param verificationId 브라우저에서 인증을 마친 뒤 받은 인증 ID
     * @return 인증된 사람의 연계정보(DI)와 생년월일
     * @throws com.bigbread.api.common.ApiException 인증이 끝나지 않았거나 실패했을 때
     */
    Result verify(String verificationId);

    /** @param di 사람마다 유일한 연계정보(원문). 저장하지 말고 해시해서만 쓴다 */
    record Result(String di, LocalDate birthDate) {
    }
}
