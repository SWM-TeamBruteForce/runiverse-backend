package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunningRoomCommand;

public interface ForceFinishRunningRoomUsecase {

    void handle(ForceFinishRunningRoomCommand command);
}
