package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;
import com.runiverse.running_service.infrastructure.redis.running.RunningComboUpdateRedisAdapter;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 콤보 통지 발행 어댑터 단위 테스트")
class RunningComboUpdateRedisAdapterTest {

    private static final Long ROOM_ID = 42L;

    @Mock
    private StringRedisTemplate redisTemplate;

    private RunningComboUpdateRedisAdapter adapter;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        adapter = new RunningComboUpdateRedisAdapter(redisTemplate, JsonMapper.builder().build());
        log = LogCapture.of(RunningComboUpdateRedisAdapter.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("발행 중 Redis가 닿지 않으면 던지지 않고 수신자 수를 key=value로 담아 ERROR로 남긴다")
    void logsRedisFailureOnPublish() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(redisTemplate).convertAndSend(anyString(), any());
        RunningComboUpdate update = new RunningComboUpdate(Set.of(UUID.randomUUID(), UUID.randomUUID()), List.of());

        // when -> 콤보는 화면 표시일 뿐이고 다음 배치가 현재 상태를 통째로 다시 나른다
        assertThatCode(() -> adapter.publish(ROOM_ID, update)).doesNotThrowAnyException();

        // then
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[러닝] 콤보 통지 발행 실패: Redis 오류 - roomId=" + ROOM_ID + ", recipientCount=2");
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
    }
}
