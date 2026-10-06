package com.runiverse.running_service.application.auth.port.in;

import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeCommand;
import com.runiverse.running_service.application.auth.command.emailverification.VerifyEmailCodeResult;

public interface VerifyEmailCodeUsecase {

    VerifyEmailCodeResult handle(VerifyEmailCodeCommand command);
}
