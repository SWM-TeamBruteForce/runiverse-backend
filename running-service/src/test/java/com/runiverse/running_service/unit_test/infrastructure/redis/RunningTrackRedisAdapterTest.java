package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.exception.RunningTrackUnavailableException;
import com.runiverse.running_service.application.running.port.out.RunningTrack;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.running.RunningTrackProperties;
import com.runiverse.running_service.infrastructure.redis.running.RunningTrackRedisAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 트랙 Redis 어댑터 단위 테스트")
class RunningTrackRedisAdapterTest {

    private static final long ROOM_ID = 125L;
    private static final Duration TTL = Duration.ofHours(6);
    private static final LocalDateTime RECORDED_AT = LocalDateTime.of(2026, 7, 25, 19, 10, 30);
    // 저장 포맷은 시각을 epoch 초로 적는다 — 구현과 같은 기준으로 계산해 둔다
    private static final long EPOCH_SECOND =
            RECORDED_AT.atZone(ZoneId.systemDefault()).toEpochSecond();

    @Mock
    private StringRedisTemplate redisTemplate;

    private RunningTrackRedisAdapter adapter;
    private UserId userId;

    @BeforeEach
    void setUp() {
        adapter = new RunningTrackRedisAdapter(redisTemplate, new RunningTrackProperties(TTL));
        userId = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    // 단말이 모두 측정한 좌표 — 인자 순서는 [순번,위도,경도,고도,정확도,속도,방위,케이던스,페이스,시각]
    private static TrackPoint point(long sequence) {
        return new TrackPoint(
                sequence, 35.17955, 129.07564, 18.4, 6.2, 2.8, 85.3, 165, 345, RECORDED_AT);
    }

    // 고도·속도·방위·케이던스·페이스를 못 잰 좌표 — Location.isValid()가 막지 않는 조합이다
    private static TrackPoint pointWithoutOptionalFields(long sequence) {
        return new TrackPoint(
                sequence, 35.17955, 129.07564, null, 6.2, null, null, null, null, RECORDED_AT);
    }

    // execute(script, keys, args...)는 가변 인자라 매처로 잡기 까다롭다 — 실제 호출을 직접 읽는다
    private Invocation execution() {
        return mockingDetails(redisTemplate).getInvocations().stream()
                .filter(invocation -> "execute".equals(invocation.getMethod().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("execute가 호출되지 않았다"));
    }

    @SuppressWarnings("unchecked")
    private List<String> scriptKeys() {
        return (List<String>) execution().getRawArguments()[1];
    }

    // [TTL, 순번, 좌표, 순번, 좌표, ...] 순으로 실린다
    private Object[] scriptArgs() {
        return (Object[]) execution().getRawArguments()[2];
    }

    @Test
    @DisplayName("좌표 본체 키와 순번 비트맵 키를 함께 넘긴다")
    void usesTrackAndSeenKeys() {
        // when
        adapter.append(ROOM_ID, userId, List.of(point(0)));

        // then -> 접미사가 :seq로 돌아가면 예전 커서 값(숫자 문자열)에 SETBIT이 걸린다.
        // '5'는 0x35라 비트 2·3·5·7이 켜진 채로 시작해 그 순번들이 조용히 버려진다
        assertThat(scriptKeys()).containsExactly(
                "running:track:" + ROOM_ID + ":" + userId.value(),
                "running:track:" + ROOM_ID + ":" + userId.value() + ":seen");
    }

    @Test
    @DisplayName("첫 인자로 TTL 초를 넘긴다")
    void passesTtlSecondsFirst() {
        // when
        adapter.append(ROOM_ID, userId, List.of(point(0)));

        // then -> 스크립트가 두 키의 EXPIRE에 쓴다
        assertThat(scriptArgs()[0]).isEqualTo("21600");
    }

    @Test
    @DisplayName("두 번째 인자로 순번 상한을 넘긴다")
    void passesMaxSequenceSecond() {
        // when
        adapter.append(ROOM_ID, userId, List.of(point(0)));

        // then -> 비트맵은 offset만큼 메모리를 잡는다. 상한이 스크립트까지 가지 않으면
        // 튄 순번 하나가 러닝 하나당 수백 MB를 물 수 있다
        assertThat(scriptArgs()[1]).isEqualTo(String.valueOf(TrackPoint.MAX_SEQUENCE));
    }

    @Test
    @DisplayName("좌표를 필드명 없는 배열 문자열로 압축한다")
    void compactsPointIntoArray() {
        // when
        adapter.append(ROOM_ID, userId, List.of(point(7)));

        // then -> [순번,위도,경도,고도,정확도,속도,방위,케이던스,페이스,시각]
        assertThat(scriptArgs()[2]).isEqualTo("7");
        assertThat(scriptArgs()[3]).isEqualTo(
                "[7,35.17955,129.07564,18.4,6.2,2.80,85.3,165,345,%d]".formatted(EPOCH_SECOND));
    }

    @Test
    @DisplayName("단말이 못 잰 값은 잘리지 않은 null로 적어 자리를 지킨다")
    void writesNullForMissingValues() {
        // given -> %.2f에 null을 넘기면 "null"이 정밀도에 잘려 "nu"가 된다.
        // 그러면 저장 문자열이 JSON이 아니게 되고 뒤 값의 자리도 밀린다

        // when
        adapter.append(ROOM_ID, userId, List.of(pointWithoutOptionalFields(0)));

        // then -> 값이 없다는 사실이 남아야 읽는 쪽이 표본에서 제외할 수 있다(erd.md avg_cadence)
        assertThat(scriptArgs()[3]).isEqualTo(
                "[0,35.17955,129.07564,null,6.2,null,null,null,null,%d]".formatted(EPOCH_SECOND));
    }

    @Test
    @DisplayName("순번이 뒤섞여 들어와도 오름차순으로 실어 보낸다")
    void sortsPointsBySequence() {
        // given -> 순번마다 독립으로 판정하므로 순서가 정확성을 좌우하지는 않는다.
        // 다만 저장 순서를 순번 순서에 맞춰 두면 load()의 정렬이 거의 공짜가 된다

        // when
        adapter.append(ROOM_ID, userId, List.of(point(2), point(0), point(1)));

        // then
        Object[] args = scriptArgs();
        assertThat(List.of(args[2], args[4], args[6])).containsExactly("0", "1", "2");
    }

    @Test
    @DisplayName("Redis가 닿지 않으면 유스케이스가 다룰 수 있는 예외로 갈아끼워 던진다")
    void translatesRedisFailure() {
        // given -> execute의 가변 인자는 매처로 잡기 까다로워 개수를 맞춘다.
        // 좌표 한 개면 [TTL, 순번 상한, 순번, 좌표] 네 개다
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .willThrow(new RedisConnectionFailureException("redis down"));

        // when & then -> 인프라 예외가 그대로 새면 presentation이 Redis를 알아야 하고,
        // BusinessException이 아니라 ERROR 통지도 못 나간다(api-spec 5-D)
        assertThatThrownBy(() -> adapter.append(ROOM_ID, userId, List.of(point(0))))
                .isInstanceOf(RunningTrackUnavailableException.class);
    }

    // 스트림에 배치가 이렇게 쌓여 있다고 둔다 — 배치 하나가 XADD 항목 하나다
    private void givenStoredBatches(String... batches) {
        List<MapRecord<String, Object, Object>> records = Arrays.stream(batches)
                .map(batch -> MapRecord.create(
                        "stream", Map.<Object, Object>of("points", batch)))
                .toList();
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        given(redisTemplate.<Object, Object>opsForStream()).willReturn(streamOperations);
        given(streamOperations.range(anyString(), any())).willReturn(records);
    }

    // append가 실제로 만든 압축 문자열 — 테스트가 포맷을 따로 흉내 내지 않게 한다
    private String compacted(TrackPoint point) {
        adapter.append(ROOM_ID, userId, List.of(point));
        return (String) scriptArgs()[3];
    }

    @Test
    @DisplayName("저장한 좌표를 값 그대로 되읽는다")
    void roundTripsPoint() {
        // given -> compact()가 쓴 문자열을 parse()로 되돌린다.
        // 자리 순서가 어긋나면 고도와 정확도가 바뀌어 여기서 걸린다
        TrackPoint original = point(7);
        givenStoredBatches("[" + compacted(original) + "]");

        // when
        RunningTrack track = adapter.load(ROOM_ID, userId);

        // then
        assertThat(track.points()).containsExactly(original);
        assertThat(track.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("배치를 쉼표로 이어 하나의 배열로 만든다")
    void joinsBatchesIntoSingleArray() {
        // given -> 10초마다 한 배치씩 쌓이므로 원본은 여러 항목에 나뉘어 있다
        adapter.append(ROOM_ID, userId, List.of(point(0), point(1), point(2)));
        Object[] args = scriptArgs();
        String first = (String) args[3];
        String second = (String) args[5];
        String third = (String) args[7];
        givenStoredBatches("[" + first + "," + second + "]", "[" + third + "]");

        // when
        RunningTrack track = adapter.load(ROOM_ID, userId);

        // then -> 배치 사이에 쉼표가 빠지면 S3에 깨진 JSON이 올라간다
        assertThat(track.raw()).isEqualTo("[" + first + "," + second + "," + third + "]");
        assertThat(track.points()).containsExactly(point(0), point(1), point(2));
    }

    @Test
    @DisplayName("나중에 메워진 좌표도 순번 순서로 되돌린다")
    void sortsBackfilledPointsBySequence() {
        // given -> 집합 dedup으로 바뀌면서 구멍을 나중에 메울 수 있게 됐다.
        // 그러면 저장 순서가 더 이상 순번 순서가 아니다 — 5,6이 3,4보다 먼저 쌓여 있다
        adapter.append(ROOM_ID, userId, List.of(point(3), point(4), point(5), point(6)));
        Object[] args = scriptArgs();
        String third = (String) args[3];
        String fourth = (String) args[5];
        String fifth = (String) args[7];
        String sixth = (String) args[9];
        givenStoredBatches("[" + fifth + "," + sixth + "]", "[" + third + "," + fourth + "]");

        // when
        RunningTrack track = adapter.load(ROOM_ID, userId);

        // then -> 종료 파이프라인은 어디에서도 정렬하지 않는다(append의 정렬이 유일).
        // 여기서 안 맞춰주면 거리·스플릿이 뒤엉킨 순서로 계산된다
        assertThat(track.points()).containsExactly(point(3), point(4), point(5), point(6));
        assertThat(track.raw()).isEqualTo(
                "[" + third + "," + fourth + "," + fifth + "," + sixth + "]");
    }

    @Test
    @DisplayName("단말이 못 잰 값은 null로 되살린다")
    void restoresMissingValuesAsNull() {
        // given
        TrackPoint original = pointWithoutOptionalFields(0);
        givenStoredBatches("[" + compacted(original) + "]");

        // when
        TrackPoint restored = adapter.load(ROOM_ID, userId).points().getFirst();

        // then -> 0으로 되살리면 평균 계산에서 없던 표본이 생긴다(erd.md avg_cadence)
        assertThat(restored).isEqualTo(original);
        assertThat(restored.altitudeMeters()).isNull();
        assertThat(restored.cadenceSpm()).isNull();
    }

    @Test
    @DisplayName("좌표를 한 번도 못 받았으면 빈 트랙을 돌려준다")
    void returnsEmptyTrackWhenNothingStored() {
        // given -> 시작하자마자 끊긴 러닝. 기록 없이 상태만 확정한다(api-spec 5-D)
        givenStoredBatches();

        // when
        RunningTrack track = adapter.load(ROOM_ID, userId);

        // then
        assertThat(track.isEmpty()).isTrue();
        assertThat(track.raw()).isEqualTo("[]");
    }

    @Test
    @DisplayName("조회 중 Redis가 닿지 않으면 유스케이스가 다룰 수 있는 예외로 갈아끼운다")
    void translatesRedisFailureOnLoad() {
        // given
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        given(redisTemplate.<Object, Object>opsForStream()).willReturn(streamOperations);
        given(streamOperations.range(anyString(), any()))
                .willThrow(new RedisConnectionFailureException("redis down"));

        // when & then
        assertThatThrownBy(() -> adapter.load(ROOM_ID, userId))
                .isInstanceOf(RunningTrackUnavailableException.class);
    }

    @Test
    @DisplayName("스크립트가 아무것도 돌려주지 않으면 0으로 본다")
    void returnsZeroWhenScriptReturnsNothing() {
        // given -> 전부 중복이라 스크립트가 일찍 빠져나온 경우

        // when
        int appended = adapter.append(ROOM_ID, userId, List.of(point(0)));

        // then
        assertThat(appended).isZero();
    }
}
