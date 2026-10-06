package com.runiverse.running_service.application.auth.port.in;

import com.runiverse.running_service.application.auth.command.refresh.RefreshCommand;
import com.runiverse.running_service.application.auth.command.refresh.RefreshResult;

public interface RefreshUsecase {

    RefreshResult handle(RefreshCommand command);
}
