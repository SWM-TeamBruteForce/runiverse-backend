package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.VerificationCodeHashPort;

public class FakeVerificationCodeHasher implements VerificationCodeHashPort {

    private static final String PREFIX = "hashed:";

    @Override
    public String hash(String code) {
        return PREFIX + code;
    }

    @Override
    public boolean matches(String code, String storedHash) {
        return hash(code).equals(storedHash);
    }
}
