package com.runiverse.running_service.infrastructure.push;

import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushSendStatus;
import com.runiverse.running_service.application.device.port.out.SendPushPort;
import com.runiverse.running_service.domain.device.vo.PushToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
public class LoggingPushAdapter implements SendPushPort {

    @Override
    public PushSendStatus send(PushToken pushToken, PushMessage message) {
        log.info("[푸시 발송 생략] title={} body={} data={}", message.title(), message.body(), message.data());
        return PushSendStatus.SENT;
    }
}
