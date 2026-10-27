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
        // 본문은 다음 줄에 둔다 — 로컬 가입·E2E가 여기서 인증 코드를 읽는다
        log.warn("[인증] 인증 메일 발송 건너뜀: 로컬 프로필 - to={}, subject={}\n{}", to, subject, body);
    }
}
