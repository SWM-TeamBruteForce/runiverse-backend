package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface MatchStreamPort {

    // 등록하고, 밀려난 이전 연결은 돌려준다
    Optional<MatchStreamConnection> register(UserId userId, MatchStreamConnection connection);

    boolean remove(UserId userId, MatchStreamConnection connection);

    // 이 인스턴스에 붙어 있으면 돌려준다. 다른 서버에 붙은 유저는 그쪽이 자기 몫을 보낸다
    Optional<MatchStreamConnection> find(UserId userId);
}
