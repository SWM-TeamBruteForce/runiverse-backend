package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.infrastructure.redis.match.MatchEventListener;
import com.runiverse.running_service.infrastructure.redis.match.MatchRoomSubscriber;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 방 채널 구독기 단위 테스트")
class MatchRoomSubscriberTest {

    private static final Long ROOM_ID = 42L;

    @Mock
    private RedisMessageListenerContainer runningChannelContainer;

    @Mock
    private MatchEventListener matchEventListener;

    private MatchRoomSubscriber subscriber;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        subscriber = new MatchRoomSubscriber(runningChannelContainer, matchEventListener);
        log = LogCapture.of(MatchRoomSubscriber.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("구독 중 Redis가 닿지 않으면 스트림은 살려 두고 원인 예외를 담아 ERROR로 남긴다")
    void logsRedisFailureOnSubscribe() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(runningChannelContainer).addMessageListener(eq(matchEventListener), any(Topic.class));

        // when -> 재연결 때 다시 붙고, 그 사이 놓친 것은 스냅샷이 복구한다
        assertThatCode(() -> subscriber.subscribe(ROOM_ID)).doesNotThrowAnyException();

        // then
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[매칭] 방 채널 구독 실패: Redis 오류 - roomId=" + ROOM_ID);
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }
}
