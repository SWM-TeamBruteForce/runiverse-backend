package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.VerificationTicketHashPort;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class FakeVerificationTicketHasher implements VerificationTicketHashPort {

    private static final String PREFIX = "sha256:";

    @Override
    public String hash(String ticket) {
        return PREFIX + Base64.getEncoder()
                .encodeToString(ticket.getBytes(StandardCharsets.UTF_8));
    }
}
