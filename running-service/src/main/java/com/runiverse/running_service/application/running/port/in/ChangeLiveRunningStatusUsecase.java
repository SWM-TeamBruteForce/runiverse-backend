package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.status.ChangeLiveRunningStatusCommand;

public interface ChangeLiveRunningStatusUsecase {

    void handle(ChangeLiveRunningStatusCommand command);
}
