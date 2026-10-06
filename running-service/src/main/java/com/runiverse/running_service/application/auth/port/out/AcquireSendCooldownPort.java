package com.runiverse.running_service.application.auth.port.out;

public interface AcquireSendCooldownPort {

    boolean tryAcquire(String email);
}
