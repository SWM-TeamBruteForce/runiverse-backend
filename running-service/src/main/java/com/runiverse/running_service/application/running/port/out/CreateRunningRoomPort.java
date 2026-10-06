package com.runiverse.running_service.application.running.port.out;


import com.runiverse.running_service.domain.running.room.RunningRoom;

public interface CreateRunningRoomPort {

    RunningRoom create(RunningRoom room);
}
