package com.runiverse.running_service.infrastructure.redis.scheduling;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// 매칭 방 채널과 달리 붙었다 떨어졌다 하지 않는다 — 뜰 때 한 번 붙는다
@Configuration
@RequiredArgsConstructor
public class ScheduleChannelConfig {

    private final RedisMessageListenerContainer runningChannelContainer;
    private final ScheduledJobListener scheduledJobListener;

    @PostConstruct
    void subscribe() {
        runningChannelContainer.addMessageListener(
                scheduledJobListener, new ChannelTopic(ScheduleChannel.JOB));
    }
}
