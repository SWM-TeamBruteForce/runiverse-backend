package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatusChange;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.running.LiveRunningStatusRedisAdapter;
import com.runiverse.running_service.infrastructure.redis.running.RunningTrackProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.DISCONNECTED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.FINISHED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.PAUSED;
import static com.runiverse.running_service.application.running.port.out.LiveRunningStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 화면 참가자 상태 Redis 어댑터 단위 테스트")
class LiveRunningStatusRedisAdapterTest {

    private static final long ROOM_ID = 125L;
    private static final Duration TTL = Duration.ofHours(6);
    private static final String TTL_SECONDS = "21600";
    // 스크립트와 약속한 '상태 없음' 표기
    private static final String NONE = "";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LiveRunningStatusRedisAdapter adapter;
    private UserId userId;

    @BeforeEach
    void setUp() {
        adapter = new LiveRunningStatusRedisAdapter(redisTemplate, new RunningTrackProperties(TTL));
        userId = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    private String statusKey() {
        return "running:track:" + ROOM_ID + ":" + userId.value() + ":status";
    }

    // 스크립트가 돌려줄 '바꾸기 전 값'을 정한다 — 가변 인자 개수와 무관하게 잡도록 배열째 매칭한다
    @SuppressWarnings("unchecked")
    private void scriptReturns(String previous) {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .willReturn(previous);
    }

    // execute(script, keys, args...)는 가변 인자라 매처로 읽기 까다롭다 — 실제 호출을 직접 읽는다
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

    private Object[] scriptArgs() {
        return (Object[]) execution().getRawArguments()[2];
    }

    @Test
    @DisplayName("참가자의 러닝 데이터와 같은 접두어 아래 status 키를 쓴다")
    void usesStatusKeyUnderTrackPrefix() {
        // given
        scriptReturns("RUNNING");

        // when
        adapter.change(ROOM_ID, userId, PAUSED);

        // then -> :dist·:seen과 같은 자리에 있어야 한 패턴으로 한 사람의 러닝 데이터가 모인다
        assertThat(scriptKeys()).containsExactly(statusKey());
    }

    // [바꿀 상태, TTL, 허용되는 이전 상태...] — 허용 목록은 canChangeFrom이 정한다
    static Stream<Arguments> changeArgs() {
        return Stream.of(
                Arguments.of(RUNNING, List.of("RUNNING", TTL_SECONDS, NONE, "RUNNING", "PAUSED", "DISCONNECTED")),
                Arguments.of(PAUSED, List.of("PAUSED", TTL_SECONDS, NONE, "RUNNING", "PAUSED", "DISCONNECTED")),
                Arguments.of(DISCONNECTED, List.of("DISCONNECTED", TTL_SECONDS, "RUNNING", "PAUSED")),
                Arguments.of(FINISHED, List.of("FINISHED", TTL_SECONDS, NONE, "RUNNING", "PAUSED", "DISCONNECTED")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changeArgs")
    @DisplayName("바꿀 상태·TTL·허용되는 이전 상태 순으로 스크립트에 넘긴다")
    void passesTransitionRuleToScript(LiveRunningStatus status, List<String> expected) {
        // given
        scriptReturns("RUNNING");

        // when
        adapter.change(ROOM_ID, userId, status);

        // then -> FINISHED가 허용 목록에 들어가면 종료 뒤 닫히는 연결이 FINISHED를 덮는다
        assertThat(scriptArgs()).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("스크립트가 돌려준 이전 값으로 바뀐 결과를 만든다")
    void buildsAppliedChange() {
        // given
        scriptReturns("RUNNING");

        // when
        LiveRunningStatusChange change = adapter.change(ROOM_ID, userId, PAUSED);

        // then
        assertThat(change.previous()).isEqualTo(RUNNING);
        assertThat(change.current()).isEqualTo(PAUSED);
        assertThat(change.changed()).isTrue();
    }

    @Test
    @DisplayName("스크립트가 빈 문자열을 돌려주면 상태가 없던 참가자다")
    void emptyPreviousMeansNoStatus() {
        // given -> 키가 없으면 Lua의 GET이 false라 빈 문자열로 바꿔 돌려준다
        scriptReturns(NONE);

        // when
        LiveRunningStatusChange change = adapter.change(ROOM_ID, userId, RUNNING);

        // then
        assertThat(change.previous()).isNull();
        assertThat(change.current()).isEqualTo(RUNNING);
        assertThat(change.changed()).isTrue();
    }

    @Test
    @DisplayName("FINISHED였으면 요청과 무관하게 FINISHED 그대로다")
    void finishedStaysFinished() {
        // given -> 허용 목록에 없어 스크립트가 쓰지 않았다
        scriptReturns("FINISHED");

        // when
        LiveRunningStatusChange change = adapter.change(ROOM_ID, userId, DISCONNECTED);

        // then
        assertThat(change.current()).isEqualTo(FINISHED);
        assertThat(change.changed()).isFalse();
    }

    @Test
    @DisplayName("변경 중 Redis가 닿지 않으면 그대로 던진다")
    void propagatesRedisFailureOnChange() {
        // given
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .willThrow(new RedisConnectionFailureException("redis down"));

        // when & then -> 표시용 값이라 받는 쪽이 로그를 남기고 넘어간다. 여기서 삼키면 그 판단을 빼앗는다
        assertThatThrownBy(() -> adapter.change(ROOM_ID, userId, PAUSED))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("저장된 상태를 읽는다")
    void loadsStoredStatus() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("PAUSED");

        // when & then
        assertThat(adapter.load(ROOM_ID, userId)).contains(PAUSED);
        verify(valueOperations).get(statusKey());
    }

    @Test
    @DisplayName("키가 없으면 비어 있다")
    void loadsEmptyWhenMissing() {
        // given -> 한 번도 붙지 않은 참가자 — 무엇으로 보여줄지는 쓰는 쪽이 정한다
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        // when & then
        assertThat(adapter.load(ROOM_ID, userId)).isEmpty();
    }

    @Test
    @DisplayName("조회 중 Redis가 닿지 않으면 그대로 던진다")
    void propagatesRedisFailureOnLoad() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString()))
                .willThrow(new RedisConnectionFailureException("redis down"));

        // when & then -> 스냅샷은 실패를 그대로 올려야 한다 — 누적 거리 조회와 같은 취급이다
        assertThatThrownBy(() -> adapter.load(ROOM_ID, userId))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
