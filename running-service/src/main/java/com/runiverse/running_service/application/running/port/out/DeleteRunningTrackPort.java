package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface DeleteRunningTrackPort {

    // RUNNING_FINISHED ack 뒤 Redis 버퍼를 비운다. TTL이 있어 안 지워도 새지는 않지만,
    // 재연결 재전송이 끝난 러닝에 다시 쌓이는 걸 막는다
    void delete(Long runningRoomId, UserId userId);
}
