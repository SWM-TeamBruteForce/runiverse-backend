package com.runiverse.running_service.infrastructure.redis.running;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class RunningChannelConfig {

    @Bean
    public RedisMessageListenerContainer runningChannelContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        // redis 주소 비번을 이 factory에서 받아서 쓰라는 의미이다.
        container.setConnectionFactory(factory);
        // 구독은 방 단위로 런타임에 붙는다 - RunningRoomSubscriber를 본다
        return container;
    }
}
