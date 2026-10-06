package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.cancel.CancelMatchCommand;

public interface CancelMatchUsecase {

    void handle(CancelMatchCommand command);
}
