package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.running.RunningDistanceRedisAdapter;
import com.runiverse.running_service.infrastructure.redis.running.RunningTrackProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 누적 거리 Redis 어댑터 단위 테스트")
class RunningDistanceRedisAdapterTest {

    private static final long ROOM_ID = 125L;
    private static final Duration TTL = Duration.ofHours(6);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RunningDistanceRedisAdapter adapter;
    private UserId userId;

    @BeforeEach
    void setUp() {
        adapter = new RunningDistanceRedisAdapter(redisTemplate, new RunningTrackProperties(TTL));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        userId = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    @Test
    @DisplayName("저장된 값을 누적 거리로 되살린다")
    void loadsStoredDistance() {
        // given -> 저장 포맷은 누적거리|마지막순번|위도|경도다
        given(valueOperations.get(anyString())).willReturn("3000.25|180|35.17955|129.07564");

        // when
        RunningDistance distance = adapter.loadDistance(ROOM_ID, userId);

        // then
        assertThat(distance.meters()).isEqualTo(3000.25);
        assertThat(distance.lastSequence()).isEqualTo(180L);
        assertThat(distance.lastLatitude()).isEqualTo(35.17955);
        assertThat(distance.lastLongitude()).isEqualTo(129.07564);
    }

    @Test
    @DisplayName("저장된 값이 없으면 빈 누적으로 시작한다")
    void startsEmptyWhenNothingStored() {
        // given
        given(valueOperations.get(anyString())).willReturn(null);

        // when & then
        assertThat(adapter.loadDistance(ROOM_ID, userId)).isEqualTo(RunningDistance.empty());
    }

    @Test
    @DisplayName("읽기에 실패하면 빈 값으로 위장하지 않고 그대로 던진다")
    void throwsWhenReadFails() {
        // given -> 살아 있는 누적을 빈 값으로 착각하면 다음 저장이 덮어쓴다
        given(valueOperations.get(anyString()))
                .willThrow(new RedisConnectionFailureException("redis down"));

        // when & then
        assertThatThrownBy(() -> adapter.loadDistance(ROOM_ID, userId))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("숫자가 깨진 값은 형식 불일치처럼 처음부터 다시 센다")
    void restartsWhenNumberIsCorrupted() {
        // given
        given(valueOperations.get(anyString())).willReturn("삼천|180|35.17955|129.07564");

        // when & then
        assertThat(adapter.loadDistance(ROOM_ID, userId)).isEqualTo(RunningDistance.empty());
    }
}
