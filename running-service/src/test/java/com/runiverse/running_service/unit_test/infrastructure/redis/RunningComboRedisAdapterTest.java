package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.running.RunningComboRedisAdapter;
import com.runiverse.running_service.infrastructure.redis.running.RunningTrackProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 판정이 아무리 정확해도 저장·복원에서 깨지면 화면에는 아무것도 안 뜬다.
// 쓴 문자열을 그대로 다시 읽혀서 같은 값으로 돌아오는지 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 콤보 Redis 어댑터 단위 테스트")
class RunningComboRedisAdapterTest {

    private static final long ROOM_ID = 42L;
    private static final Duration TTL = Duration.ofHours(7);
    // 저장 포맷이 누적거리는 소수 둘째, 속도는 셋째 자리까지라 그 안에서 고른다
    private static final Instant RECORDED_AT = Instant.ofEpochMilli(1_788_000_000_000L);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, String, String> hashOperations;

    private RunningComboRedisAdapter adapter;
    private UserId one;
    private UserId other;

    @BeforeEach
    void setUp() {
        adapter = new RunningComboRedisAdapter(redisTemplate, new RunningTrackProperties(TTL));
        one = new UserId(UuidCreator.getTimeOrderedEpoch());
        other = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    @Test
    @DisplayName("스냅샷은 저장한 값 그대로 되살아난다")
    void snapshotRoundTrips() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        RunningComboSnapshot saved =
                new RunningComboSnapshot(one, 3_433.25, RECORDED_AT, 3.125);

        // when -> 쓴 것을 그대로 다시 읽힌다
        adapter.saveSnapshot(ROOM_ID, saved);
        Map<String, String> written = written();
        given(hashOperations.entries(anyString())).willReturn(written);

        // then
        assertThat(adapter.loadSnapshots(ROOM_ID)).containsExactly(saved);
    }

    @Test
    @DisplayName("이어지는 콤보는 시작 시각과 함께 되살아난다")
    void livePairRoundTrips() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        RunningComboPair saved = new RunningComboPair(one, other, RECORDED_AT, 30, 1);

        // when
        adapter.savePairs(ROOM_ID, List.of(saved));
        Map<String, String> written = written();
        given(hashOperations.entries(anyString())).willReturn(written);

        // then
        assertThat(adapter.loadPairs(ROOM_ID)).containsExactly(saved);
    }

    @Test
    @DisplayName("끊긴 관계는 시작 시각이 비어도 최고 콤보를 들고 되살아난다")
    void brokenPairRoundTrips() {
        // given -> 최고 콤보를 들고 있어야 해서 끊겨도 지우지 않는다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        RunningComboPair saved = new RunningComboPair(one, other, null, 30, 1);

        // when
        adapter.savePairs(ROOM_ID, List.of(saved));
        Map<String, String> written = written();
        given(hashOperations.entries(anyString())).willReturn(written);

        // then
        RunningComboPair loaded = adapter.loadPairs(ROOM_ID).getFirst();
        assertThat(loaded.startedAt()).isNull();
        assertThat(loaded.inCombo()).isFalse();
        assertThat(loaded.maxComboCount()).isEqualTo(30);
        assertThat(loaded).isEqualTo(saved);
    }

    @Test
    @DisplayName("관계는 정렬된 두 참가자를 필드 키로 쓴다")
    void pairFieldIsSortedUserPair() {
        // given -> A-B와 B-A가 다른 칸으로 갈리면 한쪽이 쌓은 콤보를 다른 쪽이 못 찾는다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        // when -> 뒤집어 넣어도 같은 키가 나와야 한다
        adapter.savePairs(ROOM_ID, List.of(new RunningComboPair(other, one, RECORDED_AT, 3, 1)));

        // then
        RunningComboPair sorted = new RunningComboPair(one, other, RECORDED_AT, 3, 1);
        assertThat(written()).containsOnlyKeys(
                sorted.first().value() + "|" + sorted.second().value());
    }

    @Test
    @DisplayName("쓸 때마다 TTL을 다시 건다")
    void refreshesTtlOnEveryWrite() {
        // given -> HSET은 TTL을 갱신하지 않아 다시 걸지 않으면 러닝 도중에 방이 통째로 사라진다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        // when
        adapter.saveSnapshot(ROOM_ID, new RunningComboSnapshot(one, 100, RECORDED_AT, 1));

        // then
        verify(redisTemplate).expire(anyString(), eq(TTL));
    }

    @Test
    @DisplayName("저장할 관계가 없으면 Redis를 건드리지 않는다")
    void skipsWriteWhenNoPairs() {
        // given -> 아직 아무와도 겹치지 않은 참가자

        // when
        adapter.savePairs(ROOM_ID, List.of());

        // then
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("형식이 깨진 칸만 건너뛰고 나머지는 살린다")
    void skipsOnlyCorruptedField() {
        // given -> 방 단위 해시라 한 참가자의 값이 깨졌다고 방 전체를 포기할 이유가 없다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of(
                one.value().toString(), "3400.00|1788000000000|3.300",
                other.value().toString(), "망가진|값"));

        // when
        List<RunningComboSnapshot> loaded = adapter.loadSnapshots(ROOM_ID);

        // then
        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().userId()).isEqualTo(one);
    }

    @Test
    @DisplayName("읽기에 실패하면 빈 목록으로 위장하지 않고 그대로 던진다")
    void rethrowsLoadFailure() {
        // given -> 빈 목록으로 위장하면 비교 상대가 사라져 살아 있던 콤보가 전부 끊긴다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        willThrow(new RedisConnectionFailureException("down"))
                .given(hashOperations).entries(anyString());

        // when & then
        assertThatThrownBy(() -> adapter.loadPairs(ROOM_ID))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("저장에 실패해도 던지지 않는다")
    void swallowsSaveFailure() {
        // given -> 다음 배치가 이전 상태에서 이어 판정한다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        willThrow(new RedisConnectionFailureException("down"))
                .given(hashOperations).putAll(anyString(), anyMap());

        // when & then
        assertThatCode(() -> adapter.saveSnapshot(
                ROOM_ID, new RunningComboSnapshot(one, 100, RECORDED_AT, 1)))
                .doesNotThrowAnyException();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    // 어댑터가 Redis에 실제로 쓴 필드들 — 이 값을 그대로 읽기 경로에 되먹인다
    @SuppressWarnings("unchecked")
    private Map<String, String> written() {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(anyString(), captor.capture());
        return captor.getValue();
    }
}
