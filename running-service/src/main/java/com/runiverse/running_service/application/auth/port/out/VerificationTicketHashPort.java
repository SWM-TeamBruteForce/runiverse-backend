package com.runiverse.running_service.application.auth.port.out;

public interface VerificationTicketHashPort {

    String hash(String ticket);
}
