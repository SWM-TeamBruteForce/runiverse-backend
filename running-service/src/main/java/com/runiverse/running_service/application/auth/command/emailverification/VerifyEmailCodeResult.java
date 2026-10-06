package com.runiverse.running_service.application.auth.command.emailverification;

public record VerifyEmailCodeResult(
        String verificationTicket
) {

}
