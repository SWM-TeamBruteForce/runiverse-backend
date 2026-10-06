package com.runiverse.running_service.application.auth.port.out;

public interface ConsumeVerificationAttemptPort {

    VerificationAttempt consume(String email);
}
