package com.runiverse.running_service.infrastructure.redis.match;

import com.runiverse.running_service.application.match.exception.MatchStreamUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

// 이 인스턴스가 참가자를 들고 있는 방만 구독한다
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchRoomSubscriber {

    private final RedisMessageListenerContainer runningChannelContainer;
    private final MatchEventListener matchEventListener;

    public void subscribe(Long runningRoomId) {
        try {
            runningChannelContainer.addMessageListener(
                    matchEventListener, new ChannelTopic(MatchChannel.room(runningRoomId)));
        } catch (RuntimeException e) {
            log.error("[매칭] 방 채널 구독 실패: Redis 오류 - roomId={}", runningRoomId, e);
            throw new MatchStreamUnavailableException();
        }
    }

    public void unsubscribe(Long runningRoomId) {
        runningChannelContainer.removeMessageListener(
                matchEventListener, new ChannelTopic(MatchChannel.room(runningRoomId)));
    }
}
