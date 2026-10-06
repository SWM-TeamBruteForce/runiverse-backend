package com.runiverse.running_service.application.auth.port.out;

public interface CheckBlockedAccessTokenPort {

    boolean isBlocked(String accessTokenId);
}
