package com.runiverse.running_service.application.auth.port.out;

public interface SendEmailPort {

    void send(String to, String subject, String body);
}
