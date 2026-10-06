package com.runiverse.running_service.application.auth.port.out;

public interface ConsumeVerificationTicketPort {

    String consume(String hashedTicket);
}
