package com.runiverse.running_service.domain.running.record;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.exception.CaloriesRequiredException;
import com.runiverse.running_service.domain.running.metric.exception.WeatherCodeRequiredException;
import com.runiverse.running_service.domain.running.metric.vo.Cadence;
import com.runiverse.running_service.domain.running.metric.vo.Calories;
import com.runiverse.running_service.domain.running.metric.vo.Distance;
import com.runiverse.running_service.domain.running.metric.vo.ElapsedTime;
import com.runiverse.running_service.domain.running.metric.vo.ElevationGain;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.metric.vo.RunningPeriod;
import com.runiverse.running_service.domain.running.metric.vo.Temperature;
import com.runiverse.running_service.domain.running.metric.vo.WeatherCode;
import com.runiverse.running_service.domain.running.record.exception.SplitNumberNotSequentialException;
import com.runiverse.running_service.domain.running.record.exception.SplitPeriodNotSequentialException;
import com.runiverse.running_service.domain.running.record.exception.SplitPeriodOutOfRecordException;
import com.runiverse.running_service.domain.running.record.exception.SplitRouteNotConnectedException;
import com.runiverse.running_service.domain.running.record.exception.SplitRouteNotStartingAtOriginException;
import com.runiverse.running_service.domain.running.record.exception.SplitsRequiredException;
import com.runiverse.running_service.domain.running.record.vo.GpsTrackKey;
import com.runiverse.running_service.domain.running.record.vo.RoutePolyline;
import com.runiverse.running_service.domain.running.record.vo.RunningRecordId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
public class RunningRecord {

    private final RunningRecordId runningRecordId;
    private final RunningRoomId runningRoomId;
    private final UserId userId;
    private final Pace avgPace;
    private final Distance totalDistance;
    private final ElapsedTime totalDuration;
    private final Calories totalCalories;
    private final GpsTrackKey gpsTrackKey;
    private final RoutePolyline routePolyline;
    private final RunningPeriod period;
    private final WeatherCode weatherCode;
    private final Temperature temperature;
    // 선택 항목 — 센서·외부 API가 없으면 비어 있다
    private final Cadence avgCadence;
    private final ElevationGain totalElevationGain;
    private final List<RunningSplit> splits;

    @Builder
    private RunningRecord(Long runningRecordId, Long runningRoomId, UUID userId,
                          int avgPace, int totalDistance, int totalDuration, Integer totalCalories,
                          String gpsTrackKey, String routePolyline,
                          LocalDateTime startAt, LocalDateTime endAt,
                          Integer weatherCode, BigDecimal temperature,
                          Integer avgCadence, Integer totalElevationGain,
                          List<SplitDraft> splits) {
        this.runningRecordId = runningRecordId == null ? null : new RunningRecordId(runningRecordId);
        this.runningRoomId = new RunningRoomId(runningRoomId);
        this.userId = new UserId(userId);
        this.avgPace = new Pace(avgPace);
        this.totalDistance = new Distance(totalDistance);
        this.totalDuration = new ElapsedTime(totalDuration);
        if (totalCalories == null) {
            throw new CaloriesRequiredException();
        }
        this.totalCalories = new Calories(totalCalories);
        this.gpsTrackKey = new GpsTrackKey(gpsTrackKey);
        this.routePolyline = new RoutePolyline(routePolyline);
        this.period = new RunningPeriod(startAt, endAt);
        if (weatherCode == null) {
            throw new WeatherCodeRequiredException();
        }
        this.weatherCode = new WeatherCode(weatherCode);
        this.temperature = new Temperature(temperature);
        this.avgCadence = avgCadence == null ? null : new Cadence(avgCadence);
        this.totalElevationGain =
                totalElevationGain == null ? null : new ElevationGain(totalElevationGain);
        this.splits = assembleSplits(splits, this.period);   // 방어 복사 + 불변
    }

    // ID 관련해서
    // 러닝 종료 시 새로 만든다 — 아직 ID가 없다
    public static RunningRecordBuilder finish() {
        return builder();
    }

    // DB에서 복원한다 — ID가 있다
    public static RunningRecordBuilder restore(Long runningRecordId) {
        return builder().runningRecordId(runningRecordId);
    }

    public boolean isNew() {
        return runningRecordId == null;
    }

    public Optional<RunningRecordId> getRunningRecordId() {
        return Optional.ofNullable(runningRecordId);
    }

    // 선택값에 대한 optional 값 추가
    public Optional<Cadence> getAvgCadence() {
        return Optional.ofNullable(avgCadence);
    }

    public Optional<ElevationGain> getTotalElevationGain() {
        return Optional.ofNullable(totalElevationGain);
    }

    // 구간 하나만 봐서는 알 수 없고, 기록 전체를 봐야 아는 위반들
    private static void validateSplits(List<RunningSplit> splits, RunningPeriod recordPeriod) {
        if (splits == null || splits.isEmpty()) {
            throw new SplitsRequiredException();
        }
        for (int i = 0; i < splits.size(); i++) {
            RunningSplit split = splits.get(i);
            // 구간 번호는 1부터 빠짐없이 이어진다
            if (split.getSplitNumber().value() != i + 1) {
                throw new SplitNumberNotSequentialException();
            }
            // 구간 시각은 기록 시각을 벗어나지 않는다
            if (!recordPeriod.contains(split.getPeriod())) {
                throw new SplitPeriodOutOfRecordException();
            }
        }
        // 경로는 폴리라인 첫 점에서 시작한다
        if (splits.get(0).getRouteRange().startIndex() != 0) {
            throw new SplitRouteNotStartingAtOriginException();
        }
        // 구간 경로는 끝점을 공유하며 이어진다
        for (int i = 0; i < splits.size() - 1; i++) {
            if (!splits.get(i).getRouteRange().connectsTo(splits.get(i + 1).getRouteRange())) {
                throw new SplitRouteNotConnectedException();
            }
            // 구간 시각도 순서대로 이어진다 — 경계가 붙는 것은 허용, 겹치는 것만 막는다
            if (splits.get(i).getPeriod().endAt()
                    .isAfter(splits.get(i + 1).getPeriod().startAt())) {
                throw new SplitPeriodNotSequentialException();
            }
        }
    }

    // 구간은 기록을 거쳐야만 만들어진다 — 조립하고 기록 전체 기준 검증까지 여기서 끝낸다
    private static List<RunningSplit> assembleSplits(List<SplitDraft> drafts,
                                                     RunningPeriod recordPeriod) {
        if (drafts == null || drafts.isEmpty()) {
            throw new SplitsRequiredException();
        }
        List<RunningSplit> splits = drafts.stream().map(RunningSplit::from).toList();   // 불변
        validateSplits(splits, recordPeriod);
        return splits;
    }
}
