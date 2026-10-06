package com.runiverse.running_service.infrastructure.redis.emailverification;

import com.runiverse.running_service.application.auth.port.out.ConsumeVerificationTicketPort;
import com.runiverse.running_service.application.auth.port.out.SaveVerificationTicketPort;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationTicketRedisAdapter implements SaveVerificationTicketPort, ConsumeVerificationTicketPort {

    private final StringRedisTemplate redisTemplate;
    private final EmailVerificationProperties properties;
    private static final String EMAIL_VERIFICATION = "email_verification";
    private static final String TICKET = "ticket";

    // 인증을 마친 이메일을 티켓 해시에 묶는다
    // 티켓을 키로 한 이유는 우리가 인증한 이메일을 받기 위해서 이다.
    @Override
    public void save(String hashedTicket, String email) {
        redisTemplate.opsForValue().set(key(hashedTicket), email, properties.ticketTtl());
    }

    @Override
    public String consume(String hashedTicket) {
        return redisTemplate.opsForValue().getAndDelete(key(hashedTicket));
    }

    private String key(String hashedTicket) {
        return RedisKey.USER.of(EMAIL_VERIFICATION, TICKET, hashedTicket);
    }
}
