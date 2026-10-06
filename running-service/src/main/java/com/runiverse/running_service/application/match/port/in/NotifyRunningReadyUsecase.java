package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.ready.NotifyRunningReadyCommand;

public interface NotifyRunningReadyUsecase {

    void handle(NotifyRunningReadyCommand command);
}
