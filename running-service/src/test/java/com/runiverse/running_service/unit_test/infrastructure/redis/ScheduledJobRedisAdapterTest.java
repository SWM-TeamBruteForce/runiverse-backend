package com.runiverse.running_service.unit_test.infrastructure.redis;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import com.runiverse.running_service.infrastructure.redis.scheduling.ScheduledJobRedisAdapter;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("예약 전파 어댑터 단위 테스트")
class ScheduledJobRedisAdapterTest {

    private static final long JOB_ID = 3L;

    @Mock
    private StringRedisTemplate redisTemplate;

    private ScheduledJobRedisAdapter adapter;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        adapter = new ScheduledJobRedisAdapter(redisTemplate, JsonMapper.builder().build());
        log = LogCapture.of(ScheduledJobRedisAdapter.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @Test
    @DisplayName("전파 중 Redis가 닿지 않으면 던지지 않고 예약 ID와 원인 예외를 담아 ERROR로 남긴다")
    void logsRedisFailureOnPublish() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(redisTemplate).convertAndSend(anyString(), any());
        ScheduledJob job = ScheduledJob.builder()
                .scheduledJobId(JOB_ID)
                .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, 125L))
                .executeAt(LocalDateTime.now().plusHours(2))
                .build();

        // when -> 내 타이머는 이미 걸려 있고 정본은 DB라 다른 인스턴스도 부팅 때 되살린다
        assertThatCode(() -> adapter.publish(job)).doesNotThrowAnyException();

        // then
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[예약] 예약 전파 실패: Redis 오류 - scheduledJobId=" + JOB_ID);
        assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy().getClassName())
                .isEqualTo(RedisConnectionFailureException.class.getName());
    }
}
