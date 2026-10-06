package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;

public interface CreateMatchRoomPort {

    // 붙을 방이 없을 때 여는 1인 방
    RunningRoom create(RunningRoom room);
}
