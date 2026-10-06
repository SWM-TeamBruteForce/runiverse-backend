package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.auth.port.out.GenerateVerificationTicketPort;

public class FakeVerificationTicketGenerator implements GenerateVerificationTicketPort {

    private int sequence = 0;

    @Override
    public String generate() {
        return "issued-ticket-" + (++sequence);
    }
}
