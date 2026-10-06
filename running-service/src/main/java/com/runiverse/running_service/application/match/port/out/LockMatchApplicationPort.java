package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;

import java.util.Optional;

public interface LockMatchApplicationPort {

    // 시작 핸들러와 같은 행을 놓고 경쟁한다 — 시그니처가 같아 어댑터 메서드 하나가 둘을 만족시킨다
    Optional<RunningPlayer> lockActive(UserId userId);
}
