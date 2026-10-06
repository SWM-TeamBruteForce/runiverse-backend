package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationCommand;
import com.runiverse.running_service.application.running.command.location.UpdateRunningLocationResult;

public interface UpdateRunningLocationUsecase {

    UpdateRunningLocationResult handle(UpdateRunningLocationCommand command);
}
