package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface MatchStreamPort {

    // 등록하고, 밀려난 이전 연결은 돌려준다
    Optional<MatchStreamConnection> register(UserId userId, MatchStreamConnection connection);

    boolean remove(UserId userId, MatchStreamConnection connection);
}
