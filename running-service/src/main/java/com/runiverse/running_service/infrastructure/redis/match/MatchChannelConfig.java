package com.runiverse.running_service.infrastructure.redis.match;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// 방 채널과 달리 뜰 때 한 번 붙는다 — 옛 스트림이 어느 인스턴스에 있는지 모르므로 모든 인스턴스가 듣는다
@Configuration
@RequiredArgsConstructor
public class MatchChannelConfig {

    private final RedisMessageListenerContainer runningChannelContainer;
    private final MatchSupersedeListener matchSupersedeListener;

    @PostConstruct
    void subscribe() {
        runningChannelContainer.addMessageListener(
                matchSupersedeListener, new ChannelTopic(MatchChannel.SUPERSEDE));
    }
}
