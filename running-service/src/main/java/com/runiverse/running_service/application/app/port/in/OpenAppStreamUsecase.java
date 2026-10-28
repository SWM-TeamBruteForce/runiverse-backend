package com.runiverse.running_service.application.app.port.in;

import com.runiverse.running_service.application.app.command.stream.OpenAppStreamCommand;

public interface OpenAppStreamUsecase {

    void handle(OpenAppStreamCommand command);
}
