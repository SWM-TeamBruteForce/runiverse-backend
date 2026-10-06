package com.runiverse.running_service.application.auth.port.out;

public interface SaveVerificationCodePort {

    void save(String email, String hashedCode);
}
