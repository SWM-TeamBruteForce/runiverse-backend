package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.session.RemoveRunningSessionCommand;

public interface RemoveRunningSessionUsecase {

    void handle(RemoveRunningSessionCommand command);
}
