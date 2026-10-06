package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.stream.CloseMatchStreamCommand;

public interface CloseMatchStreamUsecase {

    void handle(CloseMatchStreamCommand command);
}
