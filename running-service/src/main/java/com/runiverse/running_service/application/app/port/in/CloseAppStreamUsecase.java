package com.runiverse.running_service.application.app.port.in;

import com.runiverse.running_service.application.app.command.stream.CloseAppStreamCommand;

public interface CloseAppStreamUsecase {

    void handle(CloseAppStreamCommand command);
}
