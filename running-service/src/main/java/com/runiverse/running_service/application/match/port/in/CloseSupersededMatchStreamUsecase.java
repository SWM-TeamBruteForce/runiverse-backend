package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.stream.CloseSupersededMatchStreamCommand;

public interface CloseSupersededMatchStreamUsecase {

    void handle(CloseSupersededMatchStreamCommand command);
}
