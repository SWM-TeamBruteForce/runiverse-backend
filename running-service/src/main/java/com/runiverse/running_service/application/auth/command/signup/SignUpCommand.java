package com.runiverse.running_service.application.auth.command.signup;

public record SignUpCommand(
        String verificationTicket,
        String password
) {

}
