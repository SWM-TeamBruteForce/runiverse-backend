package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface LoadRunningTrackPort {

    RunningTrack load(Long runningRoomId, UserId userId);
}
