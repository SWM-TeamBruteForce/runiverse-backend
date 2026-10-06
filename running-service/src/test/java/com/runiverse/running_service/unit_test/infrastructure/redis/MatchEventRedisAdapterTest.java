package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.application.match.port.out.MatchEventType;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.infrastructure.redis.match.MatchEventRedisAdapter;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 이벤트 발행 어댑터 단위 테스트")
class MatchEventRedisAdapterTest {

    private static final Long ROOM_ID = 42L;

    @Mock
    private StringRedisTemplate redisTemplate;

    private MatchEventRedisAdapter adapter;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        adapter = new MatchEventRedisAdapter(redisTemplate, JsonMapper.builder().build());
        log = LogCapture.of(MatchEventRedisAdapter.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("발행 중 Redis가 닿지 않으면 던지지 않고 원인 예외를 담아 ERROR로 남긴다")
    void logsRedisFailureOnPublish() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(redisTemplate).convertAndSend(anyString(), any());
        MatchStreamEvent event = new MatchStreamEvent(MatchEventType.MATCH_ROOM_UPDATED, ROOM_ID, null, null);

        // when -> 이미 커밋된 뒤라 되돌릴 것이 없고, 다음 갱신이나 재연결 스냅샷이 복구한다
        assertThatCode(() -> adapter.publish(event)).doesNotThrowAnyException();

        // then -> 삼켜도 Redis 장애는 서버 문제다
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[매칭] 이벤트 발행 실패: Redis 오류 - type=MATCH_ROOM_UPDATED, roomId=" + ROOM_ID);
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy().getClassName())
                .isEqualTo(RedisConnectionFailureException.class.getName());
    }
}
