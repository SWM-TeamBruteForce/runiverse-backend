package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface RunningSessionPort {

    // 등록하고, 밀려난 이전 연결은 돌려준다.
    Optional<RunningConnection> register(UserId userId, RunningConnection connection);

    boolean remove(UserId userId, RunningConnection connection);

    // 인스턴스가 들고 있는 그 유저의 연결 - 다른 서버에 있으면 비어있다.
    Optional<RunningConnection> find(UserId userId);
}
