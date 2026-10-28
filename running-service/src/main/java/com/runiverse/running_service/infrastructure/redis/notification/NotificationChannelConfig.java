package com.runiverse.running_service.infrastructure.redis.notification;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// 예약 채널처럼 뜰 때 한 번 붙는다 — 받는 사람이 어느 인스턴스에 붙을지 모르므로 모든 인스턴스가 듣는다
@Configuration
@RequiredArgsConstructor
public class NotificationChannelConfig {

    private final RedisMessageListenerContainer runningChannelContainer;
    private final UserNotificationListener userNotificationListener;

    @PostConstruct
    void subscribe() {
        runningChannelContainer.addMessageListener(
                userNotificationListener, new ChannelTopic(NotificationChannel.USER));
    }
}
