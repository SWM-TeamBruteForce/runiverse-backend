package com.runiverse.running_service.infrastructure.mail;

import com.runiverse.running_service.application.auth.port.out.SendEmailPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
public class LoggingEmailAdapter implements SendEmailPort {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[메일 발송 생략] to={} subject={}\n{}", to, subject, body);
    }
}
