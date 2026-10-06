package com.runiverse.running_service.application.auth.port.out;

public interface LoadKakaoProfilePort {

    OauthProfile load(String authorizationCode, String codeVerifier);
}
