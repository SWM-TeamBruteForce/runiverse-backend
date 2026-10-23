package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.port.out.RunningComboHistory;
import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboSegment;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateMeter;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateMeterProperties;
import com.runiverse.running_service.infrastructure.redis.running.RunningComboRedisAdapter;
import com.runiverse.running_service.infrastructure.redis.running.RunningTrackProperties;
import ch.qos.logback.classic.Level;
import com.runiverse.running_service.support.LogCapture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
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
    private static final Instant MOVED_AT = RECORDED_AT.minusSeconds(4);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, String, String> hashOperations;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private RunningComboRedisAdapter adapter;
    private UserId one;
    private UserId other;

    private LogCapture log;

    @AfterEach
    void tearDown() {
        log.stop();
    }

    @BeforeEach
    void setUp() {
        log = LogCapture.of(RunningComboRedisAdapter.class);
        // 계측은 실제 구현을 쓴다 — 문턱을 크게 잡아 지연 로그가 어댑터 로그 검증에 섞이지 않게 한다
        RunningStateMeter stateMeter = new RunningStateMeter(
                meterRegistry, new RunningStateMeterProperties(Duration.ofHours(1)));
        adapter = new RunningComboRedisAdapter(redisTemplate, new RunningTrackProperties(TTL), stateMeter);
        one = new UserId(UuidCreator.getTimeOrderedEpoch());
        other = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    // ── 스냅샷 ──

    @Test
    @DisplayName("스냅샷은 저장한 값 그대로 되살아난다")
    void snapshotRoundTrips() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        RunningComboSnapshot saved =
                new RunningComboSnapshot(
                        one, 3_433.25, RECORDED_AT, 3.125, MOVED_AT, RECORDED_AT, false, false);

        // when -> 쓴 것을 그대로 다시 읽힌다
        adapter.saveSnapshot(ROOM_ID, saved);
        Map<String, String> written = written();
        given(hashOperations.entries(anyString())).willReturn(written);

        // then
        assertThat(adapter.loadSnapshots(ROOM_ID)).containsExactly(saved);
    }

    @Test
    @DisplayName("한 번도 움직이거나 관측되지 않은 스냅샷은 시각이 비어 있는 채로 되살아난다")
    void snapshotWithoutTimesRoundTrips() {
        // given -> RUNNING_START의 첫 기준
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        RunningComboSnapshot saved =
                new RunningComboSnapshot(one, 0, RECORDED_AT, 0, null, null, false, false);

        // when
        adapter.saveSnapshot(ROOM_ID, saved);
        Map<String, String> written = written();
        given(hashOperations.entries(anyString())).willReturn(written);

        // then
        assertThat(adapter.loadSnapshots(ROOM_ID)).containsExactly(saved);
    }

    @Test
    @DisplayName("스냅샷 값에는 일시정지·종료 여부를 싣지 않는다 — 별도 칸이다")
    void snapshotValueDoesNotCarryMarks() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        // when -> 일시정지·종료였던 값을 저장해도
        adapter.saveSnapshot(ROOM_ID,
                new RunningComboSnapshot(one, 100, RECORDED_AT, 1, MOVED_AT, RECORDED_AT, true, true));

        // then -> 표시 이벤트와 위치 배치가 같은 값을 고쳐 쓰면 한쪽이 사라진다
        assertThat(written()).containsOnlyKeys(one.value().toString());
    }

    @Test
    @DisplayName("종료 표시 칸을 읽어 그 참가자의 스냅샷에 붙인다")
    void attachesFinishMarkToSnapshot() {
        // given -> 같은 해시 안에 스냅샷 칸과 종료 칸이 함께 있다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of(
                one.value().toString(), "3400.00|1788000000000|3.300|1787999996000|1788000000000",
                one.value() + ":finished", "1788000001000",
                other.value().toString(), "3410.00|1788000000000|3.300|1787999996000|1788000000000"));

        // when
        List<RunningComboSnapshot> loaded = adapter.loadSnapshots(ROOM_ID);

        // then -> 종료 칸이 스냅샷으로 읽히면 형식 불일치로 WARN이 쏟아진다
        assertThat(loaded).hasSize(2);
        assertThat(loaded).filteredOn(snapshot -> snapshot.userId().equals(one))
                .singleElement().satisfies(snapshot -> {
                    assertThat(snapshot.finished()).isTrue();
                    assertThat(snapshot.paused()).isFalse();
                });
        assertThat(loaded).filteredOn(snapshot -> snapshot.userId().equals(other))
                .singleElement().satisfies(snapshot -> assertThat(snapshot.finished()).isFalse());
        assertThat(log.messages(Level.WARN)).isEmpty();
    }

    @Test
    @DisplayName("일시정지 표시 칸을 읽어 그 참가자의 스냅샷에 붙인다")
    void attachesPauseMarkToSnapshot() {
        // given -> 같은 해시 안에 스냅샷 칸과 일시정지 칸이 함께 있다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of(
                one.value().toString(), "3400.00|1788000000000|3.300|1787999996000|1788000000000",
                one.value() + ":paused", "1788000001000",
                other.value().toString(), "3410.00|1788000000000|3.300|1787999996000|1788000000000"));

        // when
        List<RunningComboSnapshot> loaded = adapter.loadSnapshots(ROOM_ID);

        // then -> 일시정지 칸이 스냅샷으로 읽히면 형식 불일치로 WARN이 쏟아진다
        assertThat(loaded).hasSize(2);
        assertThat(loaded).filteredOn(snapshot -> snapshot.userId().equals(one))
                .singleElement().satisfies(snapshot -> assertThat(snapshot.paused()).isTrue());
        assertThat(loaded).filteredOn(snapshot -> snapshot.userId().equals(other))
                .singleElement().satisfies(snapshot -> assertThat(snapshot.paused()).isFalse());
        assertThat(log.messages(Level.WARN)).isEmpty();
    }

    @Test
    @DisplayName("스냅샷 없이 일시정지 칸만 있는 참가자는 비교 대상이 아니다")
    void ignoresPauseMarkWithoutSnapshot() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of(
                one.value() + ":paused", "1788000001000"));

        // when & then
        assertThat(adapter.loadSnapshots(ROOM_ID)).isEmpty();
    }

    @Test
    @DisplayName("갱신·관측 시각을 싣기 전 3칸 스냅샷은 기준 시각으로 채워 달리는 중으로 본다")
    void readsLegacySnapshotAsMoving() {
        // given -> 배포 순간 뛰고 있던 러닝의 값이 TTL 안에 남아 있다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of(
                one.value().toString(), "3400.00|1788000000000|3.300"));

        // when
        RunningComboSnapshot loaded = adapter.loadSnapshots(ROOM_ID).getFirst();

        // then -> 비우면 그 사람들의 콤보가 배포와 함께 모두 보류된다
        assertThat(loaded.movedAt()).isEqualTo(RECORDED_AT);
        assertThat(loaded.observedAt()).isEqualTo(RECORDED_AT);
    }

    @Test
    @DisplayName("일시정지 표시는 같은 해시의 별도 칸에 쓰고 TTL을 다시 건다")
    void marksPauseInSeparateField() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        // when
        adapter.markPaused(ROOM_ID, one);

        // then
        verify(hashOperations).put(eq("running:combo:" + ROOM_ID + ":dist"),
                eq(one.value() + ":paused"), anyString());
        verify(redisTemplate).expire("running:combo:" + ROOM_ID + ":dist", TTL);
    }

    @Test
    @DisplayName("종료 표시는 같은 해시의 별도 칸에 쓰고 TTL을 다시 건다")
    void marksFinishInSeparateField() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        // when
        adapter.markFinished(ROOM_ID, one);

        // then
        verify(hashOperations).put(eq("running:combo:" + ROOM_ID + ":dist"),
                eq(one.value() + ":finished"), anyString());
        verify(redisTemplate).expire("running:combo:" + ROOM_ID + ":dist", TTL);
    }

    @Test
    @DisplayName("일시정지 표시를 지울 때는 그 칸만 지운다")
    void clearsOnlyPauseField() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        // when
        adapter.clearPaused(ROOM_ID, one);

        // then
        verify(hashOperations).delete("running:combo:" + ROOM_ID + ":dist", one.value() + ":paused");
    }

    @Test
    @DisplayName("일시정지 표시에 실패하면 삼키지 않고 던진다 — 부르는 쪽이 로그를 남긴다")
    void rethrowsPauseMarkFailure() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        willThrow(new RedisConnectionFailureException("down"))
                .given(hashOperations).put(anyString(), anyString(), anyString());

        // when & then
        assertThatThrownBy(() -> adapter.markPaused(ROOM_ID, one))
                .isInstanceOf(RedisConnectionFailureException.class);
        assertThat(log.events(Level.ERROR)).isEmpty();
    }

    // ── 관계 ──

    @Test
    @DisplayName("이어지는 콤보는 시작 시각·쌓인 시각과 함께 되살아난다")
    void livePairRoundTrips() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        RunningComboPair saved = new RunningComboPair(one, other, MOVED_AT, RECORDED_AT, 30, 1);

        // when
        adapter.savePairs(ROOM_ID, List.of(saved));
        Map<String, String> written = written();
        given(hashOperations.entries(anyString())).willReturn(written);

        // then
        assertThat(adapter.loadPairs(ROOM_ID)).containsExactly(saved);
    }

    @Test
    @DisplayName("끊긴 관계는 시각이 비어도 최고 콤보를 들고 되살아난다")
    void brokenPairRoundTrips() {
        // given -> 최고 콤보를 들고 있어야 해서 끊겨도 지우지 않는다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        RunningComboPair saved = new RunningComboPair(one, other, null, null, 30, 1);

        // when
        adapter.savePairs(ROOM_ID, List.of(saved));
        Map<String, String> written = written();
        given(hashOperations.entries(anyString())).willReturn(written);

        // then
        RunningComboPair loaded = adapter.loadPairs(ROOM_ID).getFirst();
        assertThat(loaded.inCombo()).isFalse();
        assertThat(loaded.maxComboCount()).isEqualTo(30);
        assertThat(loaded).isEqualTo(saved);
    }

    @Test
    @DisplayName("쌓인 시각을 싣기 전 3칸 관계는 쌓인 시각만 비우고 나머지를 살린다")
    void readsLegacyPair() {
        // given -> 시작시각|최고콤보|남은봐주기
        String field = one.value() + "|" + other.value();
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of(field, "1788000000000|12|1"));

        // when
        RunningComboPair loaded = adapter.loadPairs(ROOM_ID).getFirst();

        // then -> 버리면 살아 있던 콤보와 최고 기록이 처음부터 다시 시작한다
        assertThat(loaded.startedAt()).isEqualTo(RECORDED_AT);
        assertThat(loaded.accruedAt()).isNull();
        assertThat(loaded.maxComboCount()).isEqualTo(12);
        assertThat(loaded.missLeft()).isEqualTo(1);
    }

    @Test
    @DisplayName("관계는 정렬된 두 참가자를 필드 키로 쓴다")
    void pairFieldIsSortedUserPair() {
        // given -> A-B와 B-A가 다른 칸으로 갈리면 한쪽이 쌓은 콤보를 다른 쪽이 못 찾는다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        // when -> 뒤집어 넣어도 같은 키가 나와야 한다
        adapter.savePairs(ROOM_ID, List.of(new RunningComboPair(other, one, RECORDED_AT, RECORDED_AT, 3, 1)));

        // then
        RunningComboPair sorted = new RunningComboPair(one, other, RECORDED_AT, RECORDED_AT, 3, 1);
        assertThat(written()).containsOnlyKeys(
                sorted.first().value() + "|" + sorted.second().value());
    }

    // ── 구간 ──

    @Test
    @DisplayName("구간은 쌍과 시작 시각을 칸 이름으로, 끝 시각과 유지 횟수를 값으로 쓴다")
    void writesSegmentFieldAndValue() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        RunningComboSegment segment = new RunningComboSegment(one, other, MOVED_AT, RECORDED_AT, 3);

        // when
        adapter.saveSegments(ROOM_ID, List.of(segment));

        // then -> 시작 시각까지 칸 이름에 있어야 두 서버가 같은 관계를 함께 끊어도 한 칸을 덮어쓴다
        assertThat(written()).containsExactly(Map.entry(
                one.value() + "|" + other.value() + "|" + MOVED_AT.toEpochMilli(),
                RECORDED_AT.toEpochMilli() + "|3"));
        verify(redisTemplate).expire("running:combo:" + ROOM_ID + ":seg", TTL);
    }

    @Test
    @DisplayName("닫힌 구간이 없으면 Redis를 건드리지 않는다")
    void skipsWriteWhenNoSegments() {
        // when -> 대부분의 배치가 여기서 끝난다
        adapter.saveSegments(ROOM_ID, List.of());

        // then
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("구간 저장에 실패하면 삼키지 않고 던진다 — 판정이 관계 저장을 건너뛴다")
    void rethrowsSegmentSaveFailure() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        willThrow(new RedisConnectionFailureException("down"))
                .given(hashOperations).putAll(anyString(), anyMap());

        // when & then
        assertThatThrownBy(() -> adapter.saveSegments(ROOM_ID,
                List.of(new RunningComboSegment(one, other, MOVED_AT, RECORDED_AT, 3))))
                .isInstanceOf(RedisConnectionFailureException.class);
        assertThat(log.events(Level.ERROR)).isEmpty();
    }

    // ── 공통 ──

    @Test
    @DisplayName("쓸 때마다 TTL을 다시 건다")
    void refreshesTtlOnEveryWrite() {
        // given -> HSET은 TTL을 갱신하지 않아 다시 걸지 않으면 러닝 도중에 방이 통째로 사라진다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        // when
        adapter.saveSnapshot(ROOM_ID, snapshot());

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
                one.value().toString(), "3400.00|1788000000000|3.300|1787999996000|1788000000000",
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
        assertThatCode(() -> adapter.saveSnapshot(ROOM_ID, snapshot()))
                .doesNotThrowAnyException();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("읽기에 실패하면 던지기만 하고 로그는 받는 쪽에 맡긴다")
    void doesNotLogWhenRethrowing() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        willThrow(new RedisConnectionFailureException("down"))
                .given(hashOperations).entries(anyString());

        // when
        assertThatThrownBy(() -> adapter.loadPairs(ROOM_ID))
                .isInstanceOf(RedisConnectionFailureException.class);

        // then -> 여기서도 찍으면 받는 쪽 로그와 같은 실패가 두 줄로 남는다
        assertThat(log.events(Level.ERROR)).isEmpty();
    }

    @Test
    @DisplayName("저장에 실패하면 던지지 않고 방과 대상 해시를 담아 ERROR로 남긴다")
    void logsSaveFailureAsError() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        willThrow(new RedisConnectionFailureException("down"))
                .given(hashOperations).putAll(anyString(), anyMap());

        // when
        adapter.saveSnapshot(ROOM_ID, snapshot());

        // then -> target은 메트릭 태그와 같은 값이다
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[러닝] 콤보 상태 저장 실패: Redis 오류 - roomId=" + ROOM_ID + ", target=combo_snapshot");
    }

    @Test
    @DisplayName("깨진 스냅샷 칸은 건너뛰고 그 필드를 담아 WARN으로 남긴다")
    void logsSkippedSnapshotAsWarn() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of(
                other.value().toString(), "망가진|값"));

        // when
        adapter.loadSnapshots(ROOM_ID);

        // then
        assertThat(log.messages(Level.WARN))
                .containsExactly("[러닝] 콤보 스냅샷 복원 건너뜀: 저장 형식 불일치 - field=" + other.value());
    }

    @Test
    @DisplayName("숫자가 깨진 관계 칸은 건너뛰고 값 손상으로 WARN을 남긴다")
    void logsCorruptedPairAsWarn() {
        // given
        String field = one.value() + "|" + other.value();
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of(field, "null|null|셋|0"));

        // when
        adapter.loadPairs(ROOM_ID);

        // then
        assertThat(log.messages(Level.WARN))
                .containsExactly("[러닝] 콤보 관계 복원 건너뜀: 저장 값 손상 - field=" + field);
    }

    @Test
    @DisplayName("구간 저장은 러닝 중 쓰기 한 건으로 센다")
    void measuresSegmentSave() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        // when -> HSET과 EXPIRE 두 명령이지만 포트 호출은 한 번이다
        adapter.saveSegments(ROOM_ID, List.of(new RunningComboSegment(one, other, MOVED_AT, RECORDED_AT, 3)));

        // then
        assertThat(meterRegistry.get("runiverse.running.state.access")
                .tag("store", "redis")
                .tag("target", "combo_segment")
                .tag("phase", "running")
                .tag("operation", "write")
                .tag("result", "success")
                .timer()
                .count()).isEqualTo(1);
    }

    private RunningComboSnapshot snapshot() {
        return new RunningComboSnapshot(one, 100, RECORDED_AT, 1, MOVED_AT, RECORDED_AT, false, false);
    }

    // 어댑터가 Redis에 실제로 쓴 필드들 — 이 값을 그대로 읽기 경로에 되먹인다
    @SuppressWarnings("unchecked")
    private Map<String, String> written() {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(anyString(), captor.capture());
        return captor.getValue();
    }

    // ── 방이 닫힐 때 ──

    @Test
    @DisplayName("이력은 끊긴 구간과 이어지던 관계를 함께 읽고, 시작 시각 없는 관계는 뺀다")
    void loadsHistory() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        String pairField = one.value() + "|" + other.value();
        given(hashOperations.entries("running:combo:" + ROOM_ID + ":seg")).willReturn(Map.of(
                pairField + "|" + MOVED_AT.toEpochMilli(), RECORDED_AT.toEpochMilli() + "|3"));
        given(hashOperations.entries("running:combo:" + ROOM_ID + ":pair")).willReturn(Map.of(
                pairField, "1788000000000|1788000030000|5|1"));

        // when
        RunningComboHistory history = adapter.loadHistory(ROOM_ID);

        // then
        assertThat(history.closedSegments()).containsExactly(
                new RunningComboSegment(one, other, MOVED_AT, RECORDED_AT, 3));
        assertThat(history.openPairs()).singleElement()
                .satisfies(pair -> assertThat(pair.accruedAt()).isEqualTo(RECORDED_AT.plusSeconds(30)));
    }

    @Test
    @DisplayName("끊긴 관계(시작 시각 없음)는 이어지던 관계로 넘기지 않는다")
    void skipsBrokenPairsInHistory() {
        // given -> 최고 콤보만 들고 있는 관계 — 그 구간은 이미 구간 해시에 있다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        given(hashOperations.entries("running:combo:" + ROOM_ID + ":seg")).willReturn(Map.of());
        given(hashOperations.entries("running:combo:" + ROOM_ID + ":pair")).willReturn(Map.of(
                one.value() + "|" + other.value(), "null|null|5|1"));

        // when & then
        assertThat(adapter.loadHistory(ROOM_ID).openPairs()).isEmpty();
    }

    @Test
    @DisplayName("이력 읽기는 종료 읽기 한 건으로 세고 구간 수를 함께 남긴다")
    void measuresHistoryAsFinishLoad() {
        // given -> 해시 두 개를 읽지만 DB라면 방 하나의 콤보를 한 번에 가져오는 조회다
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        String pairField = one.value() + "|" + other.value();
        given(hashOperations.entries("running:combo:" + ROOM_ID + ":seg")).willReturn(Map.of(
                pairField + "|1", "20001|2", pairField + "|30000", "50000|2"));
        given(hashOperations.entries("running:combo:" + ROOM_ID + ":pair")).willReturn(Map.of(
                pairField, "60000|80000|2|1"));

        // when
        adapter.loadHistory(ROOM_ID);

        // then
        assertThat(meterRegistry.get("runiverse.running.state.access")
                .tag("target", "combo_segment").tag("phase", "finish").tag("operation", "read")
                .tag("result", "success").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("runiverse.running.state.loaded")
                .tag("target", "combo_segment").summary().totalAmount()).isEqualTo(3);
    }

    @Test
    @DisplayName("깨진 구간 칸은 건너뛰고 WARN을 남긴다 — 옮기면 제약에 걸려 방 전체가 롤백된다")
    void skipsCorruptedSegment() {
        // given
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        String field = one.value() + "|" + other.value() + "|시작";
        given(hashOperations.entries("running:combo:" + ROOM_ID + ":seg")).willReturn(Map.of(field, "20000|2"));
        given(hashOperations.entries("running:combo:" + ROOM_ID + ":pair")).willReturn(Map.of());

        // when
        RunningComboHistory history = adapter.loadHistory(ROOM_ID);

        // then
        assertThat(history.closedSegments()).isEmpty();
        assertThat(log.messages(Level.WARN))
                .containsExactly("[러닝] 콤보 구간 복원 건너뜀: 저장 값 손상 - field=" + field);
    }

    @Test
    @DisplayName("상태 삭제는 방의 콤보 키 셋을 함께 지우고 종료 쓰기로 센다")
    void deletesAllComboKeys() {
        // when
        adapter.deleteComboState(ROOM_ID);

        // then
        verify(redisTemplate).delete(List.of(
                "running:combo:" + ROOM_ID + ":dist",
                "running:combo:" + ROOM_ID + ":pair",
                "running:combo:" + ROOM_ID + ":seg"));
        assertThat(meterRegistry.get("runiverse.running.state.access")
                .tag("target", "combo_segment").tag("phase", "finish").tag("operation", "write")
                .timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("상태 삭제가 실패해도 던지지 않고 ERROR로 남긴다 — TTL이 결국 지운다")
    void swallowsDeleteFailure() {
        // given
        given(redisTemplate.delete(org.mockito.ArgumentMatchers.<java.util.Collection<String>>any()))
                .willThrow(new RedisConnectionFailureException("down"));

        // when & then
        assertThatCode(() -> adapter.deleteComboState(ROOM_ID)).doesNotThrowAnyException();
        assertThat(log.messages(Level.ERROR))
                .containsExactly("[러닝] 콤보 상태 삭제 실패: Redis 오류 - roomId=" + ROOM_ID);
    }
}
