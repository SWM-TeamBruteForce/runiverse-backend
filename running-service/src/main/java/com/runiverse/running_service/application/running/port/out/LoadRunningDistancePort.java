package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface LoadRunningDistancePort {

    // 저장된 게 없으면 RunningDistance.empty()를 돌려준다 — 첫 배치도 같은 경로로 흐른다
    RunningDistance loadDistance(Long runningRoomId, UserId userId);
}
