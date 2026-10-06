package com.runiverse.running_service.application.running.port.out;

import java.util.UUID;

public interface PublishSupersedePort {

    // 이 유저의 진짜 연결은 winnerSessionId 하나라고 다른 인스턴스들에게 알린다.
    void publish(UUID userId, Long runningRoomId, String winnerSessionId);
}
