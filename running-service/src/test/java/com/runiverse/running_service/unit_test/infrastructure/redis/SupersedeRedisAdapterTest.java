package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.application.running.exception.RunningSessionUnavailableException;
import com.runiverse.running_service.infrastructure.redis.running.SupersedeRedisAdapter;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("이전 세션 종료 통지 발행 어댑터 단위 테스트")
class SupersedeRedisAdapterTest {

    private static final Long ROOM_ID = 42L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private StringRedisTemplate redisTemplate;

    private SupersedeRedisAdapter adapter;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        adapter = new SupersedeRedisAdapter(redisTemplate, JsonMapper.builder().build());
        log = LogCapture.of(SupersedeRedisAdapter.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("발행 중 Redis가 닿지 않으면 원인 예외를 담아 ERROR로 남기고 업무 예외로 갈아끼운다")
    void logsRedisFailureOnPublish() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(redisTemplate).convertAndSend(anyString(), any());

        // when -> Redis가 없으면 좌표 저장도 못 하니 시작을 끊는다
        assertThatThrownBy(() -> adapter.publish(USER_ID, ROOM_ID, "session-1"))
                .isInstanceOf(RunningSessionUnavailableException.class);

        // then -> 업무 예외로 갈아끼우면 원래 원인은 여기서만 보인다
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[러닝] 이전 세션 종료 통지 발행 실패: Redis 오류 - roomId=" + ROOM_ID + ", userId=" + USER_ID);
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }
}
