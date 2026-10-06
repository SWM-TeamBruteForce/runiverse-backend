package com.runiverse.running_service.infrastructure.identifier;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.auth.port.out.GenerateUserIdPort;
import com.runiverse.running_service.application.user.port.out.GenerateProfileImageIdPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidGeneratorAdapter implements GenerateUserIdPort, GenerateProfileImageIdPort {

    @Override
    public UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
