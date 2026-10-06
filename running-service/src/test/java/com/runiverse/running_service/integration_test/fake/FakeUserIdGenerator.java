package com.runiverse.running_service.integration_test.fake;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.port.out.GenerateUserIdPort;

import java.util.UUID;

public class FakeUserIdGenerator implements GenerateUserIdPort {

    @Override
    public UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
