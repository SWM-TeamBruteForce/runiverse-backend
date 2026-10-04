package com.runiverse.running_service.application.user.common;

import com.runiverse.running_service.application.user.exception.ProfileNotFoundException;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.UUID;

// 경로로 받은 남의 userId를 도메인 값으로 바꾼다 — 서버가 v7만 발급하므로 그 밖의 버전은 없는 사용자다.
// 도메인 VO 검증까지 가면 500으로 마스킹된다. 토큰 주체(본인)에는 쓰지 않는다
public final class TargetUserIdPolicy {

    private static final int ISSUED_VERSION = 7;

    private TargetUserIdPolicy() {
    }

    public static UserId resolve(UUID userId) {
        if (userId.version() != ISSUED_VERSION) {
            throw new ProfileNotFoundException();
        }
        return new UserId(userId);
    }
}
