package com.runiverse.running_service.application.auth.command.emailverification;

public record VerifyEmailCodeCommand(
        String email,
        String code
) {

}
