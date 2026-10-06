package com.runiverse.running_service.application.auth.port.out;

public interface DeleteVerificationCodePort {

    void delete(String email);
}
