package com.runiverse.running_service.infrastructure.security.hash;

import com.runiverse.running_service.application.common.port.out.PasswordHashPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Argon2PasswordHashAdapter implements PasswordHashPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String storeHash) {
        return passwordEncoder.matches(rawPassword, storeHash);
    }
}
