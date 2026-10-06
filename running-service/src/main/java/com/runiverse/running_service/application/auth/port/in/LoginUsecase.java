package com.runiverse.running_service.application.auth.port.in;

import com.runiverse.running_service.application.auth.command.login.LoginCommand;
import com.runiverse.running_service.application.auth.command.login.LoginResult;

public interface LoginUsecase {

    LoginResult handle(LoginCommand command);
}
