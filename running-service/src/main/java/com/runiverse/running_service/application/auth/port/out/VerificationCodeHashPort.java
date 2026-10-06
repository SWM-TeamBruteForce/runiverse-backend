package com.runiverse.running_service.application.auth.port.out;

public interface VerificationCodeHashPort {

    String hash(String code);

    boolean matches(String code, String storedHash);
}
