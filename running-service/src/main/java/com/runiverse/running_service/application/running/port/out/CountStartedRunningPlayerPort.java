package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

public interface CountStartedRunningPlayerPort {

    // 러닝 단계에 들어온 참가자 수 — 본인과 이미 끝낸 사람도 센다(hasStartedRunning)
    int countStartedRunning(RunningRoomId runningRoomId);
}
