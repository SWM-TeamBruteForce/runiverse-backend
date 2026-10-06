package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public interface MatchCooldownPort {

    // 제재 대상 이탈 시 건다. 만료는 TTL이 알아서 하므로 해제 호출이 없다
    void start(UserId userId, Duration cooldown);

    // 남아 있으면 해제 시각을 준다 — 409 응답에 cooldownUntil로 실린다
    Optional<LocalDateTime> until(UserId userId);
}
