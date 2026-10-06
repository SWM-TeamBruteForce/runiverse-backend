package com.runiverse.running_service.application.auth.port.out;

public interface LoadGoogleProfilePort {

    OauthProfile load(String idToken);
}
