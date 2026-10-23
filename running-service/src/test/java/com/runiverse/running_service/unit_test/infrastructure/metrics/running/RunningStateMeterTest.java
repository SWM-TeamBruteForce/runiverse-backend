package com.runiverse.running_service.unit_test.infrastructure.metrics.running;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateMeter;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateMeterProperties;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStatePhase;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateStore;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget;
import com.runiverse.running_service.support.LogCapture;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("러닝 상태 저장소 계측 단위 테스트")
public class RunningStateMeterTest {

    private static final String ACCESS = "runiverse.running.state.access";
    private static final String LOADED = "runiverse.running.state.loaded";
    private static final long ROOM_ID = 12L;
    // 어떤 접근도 넘지 않는 문턱 — 지연 로그가 찍히지 않아야 하는 테스트에 쓴다
    private static final Duration NEVER_SLOW = Duration.ofHours(1);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LogCapture logs = LogCapture.of(RunningStateMeter.class);

    @AfterEach
    void tearDown() {
        logs.stop();
    }

    private RunningStateMeter meter(Duration slowThreshold) {
        return new RunningStateMeter(registry, new RunningStateMeterProperties(slowThreshold));
    }

    private Timer access(String target, String phase, String operation, String result) {
        return registry.get(ACCESS)
                .tag("store", "redis")
                .tag("target", target)
                .tag("phase", phase)
                .tag("operation", operation)
                .tag("result", result)
                .timer();
    }

    @Test
    @DisplayName("러닝 중 읽기는 값을 그대로 돌려주고 성공으로 한 건 센다")
    void read_success() {
        // given
        RunningStateMeter meter = meter(NEVER_SLOW);

        // when
        String value = meter.read(RunningStateStore.REDIS, RunningStateTarget.DISTANCE,
                RunningStatePhase.RUNNING, ROOM_ID, () -> "1200.00|42");

        // then
        assertThat(value).isEqualTo("1200.00|42");
        assertThat(access("distance", "running", "read", "success").count()).isEqualTo(1);
        // 러닝 중 읽기는 읽은 양을 남기지 않는다 — 종료 집계 읽기만 남긴다
        assertThat(registry.find(LOADED).summary()).isNull();
    }

    @Test
    @DisplayName("값을 돌려주는 쓰기는 그 값을 그대로 돌려주고 성공으로 센다")
    void write_supplier_success() {
        // given
        RunningStateMeter meter = meter(NEVER_SLOW);

        // when
        Long appended = meter.write(RunningStateStore.REDIS, RunningStateTarget.TRACK,
                RunningStatePhase.RUNNING, ROOM_ID, () -> 5L);

        // then
        assertThat(appended).isEqualTo(5L);
        assertThat(access("track", "running", "write", "success").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("값이 없는 쓰기도 실행하고 성공으로 센다")
    void write_runnable_success() {
        // given
        RunningStateMeter meter = meter(NEVER_SLOW);
        AtomicBoolean ran = new AtomicBoolean(false);

        // when
        meter.write(RunningStateStore.REDIS, RunningStateTarget.COMBO_PAIR,
                RunningStatePhase.RUNNING, ROOM_ID, () -> ran.set(true));

        // then
        assertThat(ran).isTrue();
        assertThat(access("combo_pair", "running", "write", "success").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패한 접근도 시간을 재 실패로 세고, 예외는 삼키지 않고 그대로 던진다")
    void failure_is_recorded_and_rethrown() {
        // given
        RunningStateMeter meter = meter(NEVER_SLOW);
        IllegalStateException failure = new IllegalStateException("redis down");

        // when & then
        assertThatThrownBy(() -> meter.read(RunningStateStore.REDIS, RunningStateTarget.STATUS,
                RunningStatePhase.RUNNING, ROOM_ID, () -> {
                    throw failure;
                }))
                .isSameAs(failure);
        assertThat(access("status", "running", "read", "failure").count()).isEqualTo(1);
        assertThat(registry.find(ACCESS).tag("result", "success").timer()).isNull();
    }

    @Test
    @DisplayName("종료 집계 읽기는 finish로 세고 읽은 개수를 함께 남긴다")
    void load_records_items() {
        // given
        RunningStateMeter meter = meter(NEVER_SLOW);

        // when
        List<Integer> points = meter.load(RunningStateStore.REDIS, RunningStateTarget.TRACK, ROOM_ID,
                () -> List.of(1, 2, 3), List::size);

        // then
        assertThat(points).containsExactly(1, 2, 3);
        assertThat(access("track", "finish", "read", "success").count()).isEqualTo(1);
        DistributionSummary loaded = registry.get(LOADED)
                .tag("store", "redis")
                .tag("target", "track")
                .summary();
        assertThat(loaded.count()).isEqualTo(1);
        assertThat(loaded.totalAmount()).isEqualTo(3);
    }

    @Test
    @DisplayName("종료 집계 읽기가 실패하면 읽은 개수는 남기지 않는다")
    void load_failure_records_no_items() {
        // given
        RunningStateMeter meter = meter(NEVER_SLOW);

        // when & then
        assertThatThrownBy(() -> meter.load(RunningStateStore.REDIS, RunningStateTarget.TRACK, ROOM_ID,
                () -> {
                    throw new IllegalStateException("redis down");
                }, (List<Integer> points) -> points.size()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(access("track", "finish", "read", "failure").count()).isEqualTo(1);
        assertThat(registry.find(LOADED).summary()).isNull();
    }

    @Test
    @DisplayName("문턱을 넘긴 접근은 대상·단계·방·개수와 함께 지연 WARN을 남긴다")
    void slow_access_warns() {
        // given — 문턱 0이면 모든 접근이 넘는다
        RunningStateMeter meter = meter(Duration.ZERO);

        // when
        meter.load(RunningStateStore.REDIS, RunningStateTarget.TRACK, ROOM_ID,
                () -> List.of(1, 2), List::size);

        // then
        assertThat(logs.messages(Level.WARN)).singleElement().satisfies(message -> assertThat(message)
                .startsWith("[러닝] 상태 접근 지연 - store=redis, target=track, phase=finish, "
                        + "operation=read, roomId=12, itemCount=2, elapsedMs=")
                .endsWith("thresholdMs=0"));
    }

    @Test
    @DisplayName("문턱 안의 접근은 로그를 남기지 않는다")
    void fast_access_does_not_warn() {
        // given
        RunningStateMeter meter = meter(NEVER_SLOW);

        // when
        meter.write(RunningStateStore.REDIS, RunningStateTarget.DISTANCE,
                RunningStatePhase.RUNNING, ROOM_ID, () -> {
                });

        // then
        assertThat(logs.messages(Level.WARN)).isEmpty();
    }
}
