package com.runiverse.running_service.application.common.port.out;

public interface PasswordHashPort {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String storeHash);
}
