package com.runiverse.running_service.infrastructure.metrics.running;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

// 러닝 상태 저장소 왕복을 잰다. 포트 호출 한 번이 한 건이다 —
// Redis 명령 수로 세면 HSET+EXPIRE처럼 DB에선 한 번일 일이 두 번으로 잡혀 저장소끼리 비교가 안 된다
@Slf4j
@Component
@RequiredArgsConstructor
public class RunningStateMeter {

    private static final String ACCESS = "runiverse.running.state.access";
    private static final String LOADED = "runiverse.running.state.loaded";
    private static final String READ = "read";
    private static final String WRITE = "write";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final String ITEMS = "items";
    // Redis는 1ms 아래에서 논다 — 기본 하한(1ms)이면 Redis 분포가 첫 칸 하나에 몰려 RDS와 비교할 수 없다
    private static final Duration MIN_EXPECTED_TIME = Duration.ofNanos(100_000);
    private static final Duration MAX_EXPECTED_TIME = Duration.ofSeconds(5);
    // 1초 간격 좌표로 몇 시간 — 트랙 TTL(7h) 안에서 나올 수 있는 양을 덮는다
    private static final double MIN_EXPECTED_ITEMS = 1;
    private static final double MAX_EXPECTED_ITEMS = 30_000;
    private final MeterRegistry meterRegistry;
    private final RunningStateMeterProperties properties;

    public <T> T read(RunningStateStore store, RunningStateTarget target, RunningStatePhase phase,
                      Long runningRoomId, Supplier<T> access) {
        return measure(store, target, phase, READ, runningRoomId, access, null);
    }

    // 종료 때 러닝 하나치를 통째로 읽는다 — 시간과 함께 읽은 양을 남긴다.
    // 양이 있어야 "1,000개당 시간"으로 러닝 길이와 무관하게 저장소끼리 비교할 수 있다
    public <T> T load(RunningStateStore store, RunningStateTarget target, Long runningRoomId,
                      Supplier<T> access, ToIntFunction<T> itemCount) {
        return measure(store, target, RunningStatePhase.FINISH, READ, runningRoomId, access, itemCount);
    }

    public <T> T write(RunningStateStore store, RunningStateTarget target, RunningStatePhase phase,
                       Long runningRoomId, Supplier<T> access) {
        return measure(store, target, phase, WRITE, runningRoomId, access, null);
    }

    public void write(RunningStateStore store, RunningStateTarget target, RunningStatePhase phase,
                      Long runningRoomId, Runnable access) {
        measure(store, target, phase, WRITE, runningRoomId, () -> {
            access.run();
            return null;
        }, null);
    }

    // 실패도 시간을 잰다 — 타임아웃으로 실패한 호출이 빠지면 느린 쪽 분포가 통째로 사라진다.
    // 예외는 삼키지 않고 그대로 던진다: 로그와 폴백은 지금처럼 어댑터·핸들러가 정한다.
    // itemCount는 종료 집계 읽기(load)에만 넘어온다
    private <T> T measure(RunningStateStore store, RunningStateTarget target, RunningStatePhase phase,
                          String operation, Long runningRoomId, Supplier<T> access,
                          ToIntFunction<T> itemCount) {
        long startedAt = System.nanoTime();
        String result = FAILURE;
        Integer items = null;
        try {
            T value = access.get();
            result = SUCCESS;
            if (itemCount != null) {
                items = itemCount.applyAsInt(value);
                loaded(store, target).record(items);
            }
            return value;
        } finally {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            timer(store, target, phase, operation, result).record(elapsed);
            warnIfSlow(store, target, phase, operation, runningRoomId, items, elapsed);
        }
    }

    private Timer timer(RunningStateStore store, RunningStateTarget target, RunningStatePhase phase,
                        String operation, String result) {
        return Timer.builder(ACCESS)
                .tag("store", store.tag())
                .tag("target", target.tag())
                .tag("phase", phase.tag())
                .tag("operation", operation)
                .tag("result", result)
                .publishPercentileHistogram()
                .minimumExpectedValue(MIN_EXPECTED_TIME)
                .maximumExpectedValue(MAX_EXPECTED_TIME)
                .register(meterRegistry);
    }

    private DistributionSummary loaded(RunningStateStore store, RunningStateTarget target) {
        return DistributionSummary.builder(LOADED)
                .baseUnit(ITEMS)
                .tag("store", store.tag())
                .tag("target", target.tag())
                .publishPercentileHistogram()
                .minimumExpectedValue(MIN_EXPECTED_ITEMS)
                .maximumExpectedValue(MAX_EXPECTED_ITEMS)
                .register(meterRegistry);
    }

    private void warnIfSlow(RunningStateStore store, RunningStateTarget target, RunningStatePhase phase,
                            String operation, Long runningRoomId, Integer items, Duration elapsed) {
        if (elapsed.compareTo(properties.slowThreshold()) <= 0) {
            return;
        }
        log.warn("[러닝] 상태 접근 지연 - store={}, target={}, phase={}, operation={}, roomId={}, "
                        + "itemCount={}, elapsedMs={}, thresholdMs={}",
                store.tag(), target.tag(), phase.tag(), operation, runningRoomId,
                items, elapsed.toMillis(), properties.slowThreshold().toMillis());
    }
}
