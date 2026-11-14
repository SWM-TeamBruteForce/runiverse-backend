package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.match.MatchChannel;
import com.runiverse.running_service.infrastructure.redis.match.MatchSupersedeMessage;
import com.runiverse.running_service.infrastructure.redis.match.MatchSupersedeRedisAdapter;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 스트림 대체 알림 발행 어댑터 단위 테스트")
class MatchSupersedeRedisAdapterTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final String WINNER_CONNECTION = "conn-winner";

    @Mock
    private StringRedisTemplate redisTemplate;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private MatchSupersedeRedisAdapter adapter;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        adapter = new MatchSupersedeRedisAdapter(redisTemplate, jsonMapper);
        log = LogCapture.of(MatchSupersedeRedisAdapter.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("방 채널이 아니라 공용 채널로 사용자와 이긴 연결 ID를 보낸다")
    void publishesToSharedChannel() {
        // when
        adapter.publish(new UserId(USER_ID), WINNER_CONNECTION);

        // then -> 방을 옮기면 옛 연결을 든 인스턴스는 새 방을 구독하지 않는다 — 모든 인스턴스가 듣는 채널이어야 한다
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(MatchChannel.SUPERSEDE), body.capture());
        assertThat(jsonMapper.readValue(body.getValue(), MatchSupersedeMessage.class))
                .isEqualTo(new MatchSupersedeMessage(USER_ID, WINNER_CONNECTION));
    }

    @Test
    @DisplayName("발행 중 Redis가 닿지 않으면 던지지 않고 원인 예외를 담아 ERROR로 남긴다")
    void logsRedisFailureOnPublish() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(redisTemplate).convertAndSend(anyString(), any());

        // when -> 새 연결은 이미 붙었다. 던지면 스냅샷을 못 받고 연결만 실패한다
        assertThatCode(() -> adapter.publish(new UserId(USER_ID), WINNER_CONNECTION)).doesNotThrowAnyException();

        // then
        assertThat(log.messages(Level.ERROR)).containsExactly(
                "[매칭] 이전 스트림 종료 알림 발행 실패: Redis 오류 - winnerConnectionId=" + WINNER_CONNECTION);
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy().getClassName())
                .isEqualTo(RedisConnectionFailureException.class.getName());
    }
}
