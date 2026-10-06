package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.exception.RunningSessionUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

// 이 인스턴스가 참가자를 들고 있는 방만 구독한다
@Slf4j
@Component
@RequiredArgsConstructor
public class RunningRoomSubscriber {

    private final RedisMessageListenerContainer runningChannelContainer;
    private final RunningRoomListener runningRoomListener;

    public void subscribe(Long runningRoomId) {
        try {
            runningChannelContainer.addMessageListener(
                    runningRoomListener, new ChannelTopic(RunningChannel.room(runningRoomId)));
        } catch (RuntimeException e) {
            log.error("[러닝] 방 채널 구독 실패: Redis 오류 - roomId={}", runningRoomId, e);
            throw new RunningSessionUnavailableException();
        }
        log.debug("[러닝] 방 채널 구독 성공 - roomId={}", runningRoomId);
    }

    public void unsubscribe(Long runningRoomId) {
        runningChannelContainer.removeMessageListener(
                runningRoomListener, new ChannelTopic(RunningChannel.room(runningRoomId)));
        log.debug("[러닝] 방 채널 구독 해제 성공 - roomId={}", runningRoomId);
    }
}
