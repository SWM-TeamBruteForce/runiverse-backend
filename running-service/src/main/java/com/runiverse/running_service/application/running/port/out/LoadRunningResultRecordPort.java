package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Optional;

public interface LoadRunningResultRecordPort {

    Optional<RunningResultRecord> loadRecord(RunningRoomId runningRoomId, UserId userId);
}
