package com.runiverse.running_service.application.auth.port.out;

public interface CheckDailySendLimitPort {

    boolean tryConsume(String email);
}
