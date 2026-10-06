package com.runiverse.running_service.infrastructure.security.emailverification;

import com.runiverse.running_service.application.auth.port.out.GenerateVerificationCodePort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureRandomVerificationCodeAdapter implements GenerateVerificationCodePort {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
