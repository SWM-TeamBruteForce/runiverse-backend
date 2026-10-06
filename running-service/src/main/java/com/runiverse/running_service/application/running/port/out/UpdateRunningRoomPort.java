package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;

public interface UpdateRunningRoomPort {

    void update(RunningRoom room);
}
