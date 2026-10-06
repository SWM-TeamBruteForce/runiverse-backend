package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.application.running.exception.RunningSessionUnavailableException;
import com.runiverse.running_service.infrastructure.redis.running.RunningRoomListener;
import com.runiverse.running_service.infrastructure.redis.running.RunningRoomSubscriber;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 방 채널 구독기 단위 테스트")
class RunningRoomSubscriberTest {

    private static final Long ROOM_ID = 42L;

    @Mock
    private RedisMessageListenerContainer runningChannelContainer;

    @Mock
    private RunningRoomListener runningRoomListener;

    private RunningRoomSubscriber subscriber;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        subscriber = new RunningRoomSubscriber(runningChannelContainer, runningRoomListener);
        log = LogCapture.of(RunningRoomSubscriber.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("구독 중 Redis가 닿지 않으면 원인 예외를 담아 ERROR로 남기고 업무 예외로 갈아끼운다")
    void logsRedisFailureOnSubscribe() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(runningChannelContainer).addMessageListener(eq(runningRoomListener), any(Topic.class));

        // when
        assertThatThrownBy(() -> subscriber.subscribe(ROOM_ID))
                .isInstanceOf(RunningSessionUnavailableException.class);

        // then -> 업무 예외로 갈아끼우면 원래 원인은 여기서만 보인다
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[러닝] 방 채널 구독 실패: Redis 오류 - roomId=" + ROOM_ID);
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy().getClassName())
                .isEqualTo(RedisConnectionFailureException.class.getName());
    }
}
