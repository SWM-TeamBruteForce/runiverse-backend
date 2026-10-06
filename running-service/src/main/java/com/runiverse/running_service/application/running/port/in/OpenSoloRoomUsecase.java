package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomCommand;
import com.runiverse.running_service.application.running.command.solo.OpenSoloRoomResult;

public interface OpenSoloRoomUsecase {

    OpenSoloRoomResult handle(OpenSoloRoomCommand command);
}
