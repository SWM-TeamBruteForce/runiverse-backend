package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.start.StartRunningCommand;
import com.runiverse.running_service.application.running.command.start.StartRunningResult;

public interface StartRunningUsecase {

    StartRunningResult handle(StartRunningCommand command);
}
