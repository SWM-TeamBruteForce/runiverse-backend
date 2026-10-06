package com.runiverse.running_service.application.auth.port.in;

import com.runiverse.running_service.application.auth.command.logout.LogoutCommand;

public interface LogoutUsecase {

    void handle(LogoutCommand command);
}
