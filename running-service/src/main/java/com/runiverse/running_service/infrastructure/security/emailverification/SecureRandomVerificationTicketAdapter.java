package com.runiverse.running_service.infrastructure.security.emailverification;

import com.runiverse.running_service.application.auth.port.out.GenerateVerificationTicketPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecureRandomVerificationTicketAdapter implements GenerateVerificationTicketPort {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TICKET_BYTES = 32;

    @Override
    public String generate() {
        byte[] ticket = new byte[TICKET_BYTES];
        RANDOM.nextBytes(ticket);
        return ENCODER.encodeToString(ticket);
    }
}
