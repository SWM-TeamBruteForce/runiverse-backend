package com.runiverse.running_service.application.auth.port.in;

import com.runiverse.running_service.application.auth.command.emailverification.SendEmailVerificationCommand;

public interface SendEmailVerificationUsecase {

    void handle(SendEmailVerificationCommand command);
}
