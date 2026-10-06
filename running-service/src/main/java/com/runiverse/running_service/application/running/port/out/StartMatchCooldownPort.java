package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.Duration;

public interface StartMatchCooldownPort {

    // 러닝 중 조기 종료 제재도 매칭 신청을 막는다. 러닝은 걸기만 하고 읽지 않는다
    void start(UserId userId, Duration cooldown);
}
