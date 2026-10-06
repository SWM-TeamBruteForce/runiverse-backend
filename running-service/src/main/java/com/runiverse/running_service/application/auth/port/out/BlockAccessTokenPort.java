package com.runiverse.running_service.application.auth.port.out;

public interface BlockAccessTokenPort {

    void block(String accessTokenId);
}
