package com.runiverse.running_service.infrastructure.redis.notification;

import com.runiverse.running_service.application.notification.command.push.PushUserNotificationCommand;
import com.runiverse.running_service.application.notification.port.in.PushUserNotificationUsecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationListener implements MessageListener {

    private final JsonMapper jsonMapper;
    private final PushUserNotificationUsecase pushUserNotificationUsecase;

    // 발행한 인스턴스도 자기 메시지를 받는다 — 받는 사람이 여기 붙어 있으면 여기서 보낸다
    @Override
    public void onMessage(Message message, byte[] pattern) {
        UserNotificationMessage payload;
        try {
            payload = jsonMapper.readValue(message.getBody(), UserNotificationMessage.class);
        } catch (JacksonException e) {
            // 깨진 메시지 한 건 때문에 이후 수신이 막히면 안 된다
            log.error("[알림] 알림 전파 메시지 파싱 실패: 메시지 형식 불일치 - channel={}",
                    new String(message.getChannel(), StandardCharsets.UTF_8), e);
            return;
        }
        pushUserNotificationUsecase.handle(new PushUserNotificationCommand(payload.userId(), payload.notificationId()));
    }
}
