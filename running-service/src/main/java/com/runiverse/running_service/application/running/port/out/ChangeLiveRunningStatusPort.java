package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface ChangeLiveRunningStatusPort {

    // 읽고-판정하고-쓰기를 한 번에 한다 — 끊김과 종료가 엇갈려도 FINISHED가 덮이지 않는다.
    // 허용되지 않는 전이면 쓰지 않고 지금 상태를 그대로 돌려준다
    LiveRunningStatusChange change(Long runningRoomId, UserId userId, LiveRunningStatus status);
}
