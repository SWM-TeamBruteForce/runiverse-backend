package com.runiverse.running_service.domain.running.record;

import com.runiverse.running_service.domain.running.metric.exception.CaloriesRequiredException;
import com.runiverse.running_service.domain.running.metric.vo.Cadence;
import com.runiverse.running_service.domain.running.metric.vo.Calories;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.ElapsedTime;
import com.runiverse.running_service.domain.running.metric.vo.ElevationChange;
import com.runiverse.running_service.domain.running.metric.vo.RunningPeriod;
import com.runiverse.running_service.domain.running.metric.vo.SplitPace;
import com.runiverse.running_service.domain.running.record.vo.RouteRange;
import com.runiverse.running_service.domain.running.record.vo.RunningSplitId;
import com.runiverse.running_service.domain.running.record.vo.SplitNumber;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Optional;

@Getter
public class RunningSplit {

    private final RunningSplitId runningSplitId;
    private final SplitNumber splitNumber;
    private final SplitPace avgPace;
    private final Distance distance;
    private final ElapsedTime duration;
    private final RouteRange routeRange;
    private final RunningPeriod period;
    private final Calories calories;
    // 선택 항목
    private final Cadence avgCadence;
    private final ElevationChange elevationChange;

    @Builder(access = AccessLevel.PRIVATE)
    private RunningSplit(Long runningSplitId, int splitNumber, int avgPace, int distance, int duration,
                         int routeStartIndex, int routeEndIndex,
                         LocalDateTime startAt, LocalDateTime endAt,
                         Integer calories, // calories만 0이 나올가능성이 가장 크다
                         Integer avgCadence, Integer elevationChange) {
        this.runningSplitId = runningSplitId == null ? null : new RunningSplitId(runningSplitId);
        this.splitNumber = new SplitNumber(splitNumber);
        this.avgPace = new SplitPace(avgPace);
        this.distance = new Distance(distance);
        this.duration = new ElapsedTime(duration);
        this.routeRange = new RouteRange(routeStartIndex, routeEndIndex);
        this.period = new RunningPeriod(startAt, endAt);
        if (calories == null) {
            throw new CaloriesRequiredException();
        }
        this.calories = new Calories(calories);
        this.avgCadence = avgCadence == null ? null : new Cadence(avgCadence);
        this.elevationChange = elevationChange == null ? null : new ElevationChange(elevationChange);
    }

    // 기록이 조립할 때만 쓰는 통로 — 밖에서는 SplitDraft까지만 만들 수 있다
    static RunningSplit from(SplitDraft draft) {
        return RunningSplit.builder()
                .runningSplitId(draft.runningSplitId())
                .splitNumber(draft.splitNumber())
                .avgPace(draft.avgPace())
                .distance(draft.distance())
                .duration(draft.duration())
                .routeStartIndex(draft.routeStartIndex())
                .routeEndIndex(draft.routeEndIndex())
                .startAt(draft.startAt())
                .endAt(draft.endAt())
                .calories(draft.calories())
                .avgCadence(draft.avgCadence())
                .elevationChange(draft.elevationChange())
                .build();
    }

    // running_id가 null일 수 있음으로 처리해주어야 한다.
    public boolean isNew() {
        return runningSplitId == null;
    }

    public Optional<RunningSplitId> getRunningSplitId() {
        return Optional.ofNullable(runningSplitId);
    }

    // cadence는 있을 수도 없을 수도
    public Optional<Cadence> getAvgCadence() {
        return Optional.ofNullable(avgCadence);
    }

    public Optional<ElevationChange> getElevationChange() {
        return Optional.ofNullable(elevationChange);
    }
}
