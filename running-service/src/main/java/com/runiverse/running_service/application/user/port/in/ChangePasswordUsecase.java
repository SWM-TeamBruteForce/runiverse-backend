package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.password.ChangePasswordCommand;

public interface ChangePasswordUsecase {

    void handle(ChangePasswordCommand command);
}
