package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.vo.ProviderId;

public interface UnlinkKakaoPort {

    // 실패해도 예외를 던지지 않는다 — 탈퇴는 이미 커밋됐고 카카오 장애로 되돌릴 수 없다
    void unlink(ProviderId providerId);
}
