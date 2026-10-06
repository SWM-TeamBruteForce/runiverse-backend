package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Optional;

public interface LoadRunningRoomPort {

    // 세션까지 함께 복원한다 — 방 애그리거트는 세션 없이는 판정할 수 없다
    Optional<RunningRoom> loadById(RunningRoomId runningRoomId);
}
