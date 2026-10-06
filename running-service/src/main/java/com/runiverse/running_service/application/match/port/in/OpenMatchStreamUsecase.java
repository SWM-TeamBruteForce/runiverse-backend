package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.stream.OpenMatchStreamCommand;

public interface OpenMatchStreamUsecase {

    void handle(OpenMatchStreamCommand command);
}
