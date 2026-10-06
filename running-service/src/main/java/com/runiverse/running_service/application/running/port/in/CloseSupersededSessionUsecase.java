package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.session.CloseSupersededSessionCommand;

public interface CloseSupersededSessionUsecase {

    void handle(CloseSupersededSessionCommand command);
}
