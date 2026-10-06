package com.runiverse.running_service.infrastructure.redis.match;

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
            // 러닝과 달리 던지지 않는다 — 구독에 실패해도 스트림 연결 자체는 살려 둔다.
            // 재연결 때 다시 붙고, 그 사이 놓친 것은 스냅샷이 복구한다
            log.error("[매칭] 방 채널 구독 실패: Redis 오류 - roomId={}", runningRoomId, e);
        }
    }

    public void unsubscribe(Long runningRoomId) {
        runningChannelContainer.removeMessageListener(
                matchEventListener, new ChannelTopic(MatchChannel.room(runningRoomId)));
    }
}
