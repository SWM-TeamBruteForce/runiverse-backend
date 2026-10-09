package com.runiverse.running_service.infrastructure.metrics;

import com.runiverse.running_service.application.running.common.TrackFilterSummary;
import com.runiverse.running_service.application.running.port.out.RecordRunningMetricPort;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RunningMetricAdapter implements RecordRunningMetricPort {

    private static final String FILTERED = "runiverse.running.finish.filtered";
    private static final String GAPS = "runiverse.running.finish.gaps";
    private static final String GOAL = "runiverse.running.location.goal";
    private static final String GOAL_PENDING = "runiverse.running.finish.goalpending";
    private static final String METERS = "meters";
    // 분포를 보려고 히스토그램을 켠다 — 구간을 목표 거리 범위로 묶어 시계열이 불필요하게 늘지 않게 한다
    private static final double MIN_EXPECTED_METERS = 1;
    private static final double MAX_EXPECTED_METERS = 10_000;
    private final MeterRegistry meterRegistry;

    @Override
    public void trackFiltered(TrackFilterSummary summary) {
        recordFiltered("accuracy", summary.accuracyRemovedMeters());
        recordFiltered("spike", summary.spikeRemovedMeters());
        recordFiltered("gap", summary.gapRejectedMeters());
        recordFiltered("stop", summary.stopRemovedMeters());
        meterRegistry.counter(GAPS, "decision", "accepted").increment(summary.gapAcceptedCount());
        meterRegistry.counter(GAPS, "decision", "rejected").increment(summary.gapRejectedCount());
    }

    @Override
    public void autoFinishChecked(boolean finished) {
        meterRegistry.counter(GOAL, "decision", finished ? "finished" : "pending").increment();
    }

    @Override
    public void userFinishDeferred(int remainingMeters) {
        meters(GOAL_PENDING).record(remainingMeters);
    }

    // 뺀 거리가 0인 러닝도 기록한다 — 빼면 "거른 게 없는 러닝"이 분포에서 사라져 비율을 낼 수 없다
    private void recordFiltered(String filter, double meters) {
        DistributionSummary.builder(FILTERED)
                .baseUnit(METERS)
                .tag("filter", filter)
                .publishPercentileHistogram()
                .minimumExpectedValue(MIN_EXPECTED_METERS)
                .maximumExpectedValue(MAX_EXPECTED_METERS)
                .register(meterRegistry)
                .record(meters);
    }

    private DistributionSummary meters(String name) {
        return DistributionSummary.builder(name)
                .baseUnit(METERS)
                .publishPercentileHistogram()
                .minimumExpectedValue(MIN_EXPECTED_METERS)
                .maximumExpectedValue(MAX_EXPECTED_METERS)
                .register(meterRegistry);
    }
}
