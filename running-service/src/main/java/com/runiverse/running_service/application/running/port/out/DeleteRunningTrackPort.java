package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface DeleteRunningTrackPort {

    // RUNNING_FINISHED ack 뒤 Redis 버퍼를 비운다. TTL이 있어 안 지워도 새지는 않는다
    void delete(Long runningRoomId, UserId userId);
}
