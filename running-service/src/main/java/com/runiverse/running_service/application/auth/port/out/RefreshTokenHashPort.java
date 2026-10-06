package com.runiverse.running_service.application.auth.port.out;

public interface RefreshTokenHashPort {

    String hash(String refreshToken);

    boolean matches(String refreshToken, String storedHash);
}
