package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

public interface ExistsRunningPlayerPort {

    // 아직 RUNNING으로 남은 참가자가 있는지 — 전원 종료돼야 방을 FINISHED로 닫는다.
    // 참가자 전체를 불러와 세는 대신 이것만 묻는다
    boolean existsRunning(RunningRoomId runningRoomId);
}
