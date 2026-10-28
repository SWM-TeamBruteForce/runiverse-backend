package com.runiverse.running_service.application.app.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Optional;

public interface AppStreamPort {

    // 등록하고, 밀려난 이전 연결은 돌려준다
    Optional<AppStreamConnection> register(UserId userId, AppStreamConnection connection);

    // 새 연결이 이미 자리를 가져갔으면 지우지 않는다 — 지웠으면 true
    boolean remove(UserId userId, AppStreamConnection connection);

    // 이 인스턴스에 붙어 있으면 돌려준다. 다른 서버에 붙은 유저는 그쪽이 자기 몫을 보낸다
    Optional<AppStreamConnection> find(UserId userId);
}
