package com.runiverse.running_service.infrastructure.redis.notification;

import com.runiverse.running_service.application.notification.port.out.PublishUserNotificationPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationRedisAdapter implements PublishUserNotificationPort {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(UserId userId, Long notificationId) {
        UserNotificationMessage message = new UserNotificationMessage(userId.value(), notificationId);
        try {
            redisTemplate.convertAndSend(NotificationChannel.USER, jsonMapper.writeValueAsString(message));
        } catch (RuntimeException e) {
            // 던지지 않는다 — 이미 커밋된 뒤라 되돌릴 것이 없고,
            // 알림은 DB에 있어 받는 사람이 다음에 목록(2-4)을 열면 보인다
            log.error("[알림] 알림 전파 실패: Redis 오류 - notificationId={}", notificationId, e);
        }
    }
}
