package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.common.port.out.PasswordHashPort;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class FakePasswordHasher implements PasswordHashPort {

    private static final String PREFIX = "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHQ$";

    @Override
    public String hash(String rawPassword) {
        return PREFIX + Base64.getEncoder()
                .encodeToString(rawPassword.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean matches(String rawPassword, String storeHash) {
        return hash(rawPassword).equals(storeHash);
    }
}
