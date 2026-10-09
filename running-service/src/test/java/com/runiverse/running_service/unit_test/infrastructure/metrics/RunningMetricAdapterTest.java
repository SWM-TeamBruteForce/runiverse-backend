package com.runiverse.running_service.unit_test.infrastructure.metrics;

import com.runiverse.running_service.application.running.common.TrackFilterSummary;
import com.runiverse.running_service.infrastructure.metrics.RunningMetricAdapter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("러닝 메트릭 어댑터 단위 테스트")
public class RunningMetricAdapterTest {

    private static final String FILTERED = "runiverse.running.finish.filtered";
    private static final String GAPS = "runiverse.running.finish.gaps";
    private static final String GOAL = "runiverse.running.location.goal";
    private static final String GOAL_PENDING = "runiverse.running.finish.goalpending";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RunningMetricAdapter adapter = new RunningMetricAdapter(registry);

    // 정확도 10m, 튐 20m, 거부한 관측 안 된 칸 30m, 정지 40m를 뺐다. 관측 안 된 칸은 2개 인정·1개 거부
    private static TrackFilterSummary summary() {
        return new TrackFilterSummary(
                1_000, 900,
                1, 10,
                1, 0, 20,
                3, 2, 1,
                100, 30,
                300, 60,
                2, 40, 40,
                25, 5,
                0);
    }

    private DistributionSummary filtered(String filter) {
        return registry.get(FILTERED).tag("filter", filter).summary();
    }

    @Test
    @DisplayName("필터 단계별로 뺀 거리를 단계 태그로 나눠 기록한다")
    void recordsRemovedMetersPerFilter() {
        // when
        adapter.trackFiltered(summary());

        // then
        assertThat(filtered("accuracy").totalAmount()).isEqualTo(10.0);
        assertThat(filtered("spike").totalAmount()).isEqualTo(20.0);
        assertThat(filtered("gap").totalAmount()).isEqualTo(30.0);
        assertThat(filtered("stop").totalAmount()).isEqualTo(40.0);
        assertThat(filtered("stop").getId().getBaseUnit()).isEqualTo("meters");
    }

    @Test
    @DisplayName("뺀 거리가 0인 러닝도 표본으로 남긴다")
    void recordsZeroRemovedMeters() {
        // given -> 아무것도 거르지 않은 러닝
        TrackFilterSummary clean = new TrackFilterSummary(
                1_000, 1_000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        // when
        adapter.trackFiltered(clean);

        // then -> 빠지면 "거른 게 없는 러닝"이 분포에서 사라져 비율을 낼 수 없다
        assertThat(filtered("stop").count()).isEqualTo(1);
        assertThat(filtered("stop").totalAmount()).isZero();
    }

    @Test
    @DisplayName("관측 안 된 칸의 인정·거부 수를 판정 태그로 나눠 센다")
    void countsGapDecisions() {
        // when -> 두 러닝이 확정됐다
        adapter.trackFiltered(summary());
        adapter.trackFiltered(summary());

        // then
        assertThat(registry.get(GAPS).tag("decision", "accepted").counter().count()).isEqualTo(4.0);
        assertThat(registry.get(GAPS).tag("decision", "rejected").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("자동 종료 재확인을 확정·미룸으로 나눠 센다")
    void countsAutoFinishDecisions() {
        // when
        adapter.autoFinishChecked(true);
        adapter.autoFinishChecked(false);
        adapter.autoFinishChecked(false);

        // then
        assertThat(registry.get(GOAL).tag("decision", "finished").counter().count()).isEqualTo(1.0);
        assertThat(registry.get(GOAL).tag("decision", "pending").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("미뤄진 사용자 종료의 남은 거리를 분포로 남긴다")
    void recordsDeferredRemainingMeters() {
        // when
        adapter.userFinishDeferred(40);
        adapter.userFinishDeferred(250);

        // then
        DistributionSummary pending = registry.get(GOAL_PENDING).summary();
        assertThat(pending.count()).isEqualTo(2);
        assertThat(pending.totalAmount()).isEqualTo(290.0);
        assertThat(pending.max()).isEqualTo(250.0);
    }
}
