package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

public interface ExistsRunningRecordPort {

    // 이 방에 저장된 기록이 하나라도 있는지 — 방을 FINISHED로 닫을지 CANCELLED로 닫을지 가른다.
    // 기록 전체를 불러와 세는 대신 이것만 묻는다
    boolean existsInRoom(RunningRoomId runningRoomId);
}
