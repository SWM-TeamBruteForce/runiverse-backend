package com.runiverse.running_service.application.auth.port.out;

public interface SaveVerificationTicketPort {

    void save(String hashedTicket, String email);
}
