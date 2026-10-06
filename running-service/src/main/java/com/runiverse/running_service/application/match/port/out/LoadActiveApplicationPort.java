package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;

import java.util.Optional;

public interface LoadActiveApplicationPort {

    // 활성 신청은 유저당 하나다 — 커맨드에 없는 신청 식별자와 상태를 여기서 얻는다
    Optional<RunningPlayer> loadActive(UserId userId);
}
