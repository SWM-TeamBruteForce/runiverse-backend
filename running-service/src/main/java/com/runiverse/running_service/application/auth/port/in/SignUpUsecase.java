package com.runiverse.running_service.application.auth.port.in;

import com.runiverse.running_service.application.auth.command.signup.SignUpCommand;
import com.runiverse.running_service.application.auth.command.signup.SignUpResult;

public interface SignUpUsecase {

    SignUpResult handle(SignUpCommand command);
}
