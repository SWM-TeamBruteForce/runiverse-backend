package com.runiverse.running_service.application.auth.port.out;

public interface CheckEmailDuplicatePort {

    boolean existsByEmail(String email);
}
