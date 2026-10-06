package com.runiverse.running_service.domain.running.record;

import java.time.LocalDateTime;

public record SplitDraft(Long runningSplitId, int splitNumber, int avgPace,
                         int distance, int duration,
                         int routeStartIndex, int routeEndIndex,
                         LocalDateTime startAt, LocalDateTime endAt,
                         Integer calories, Integer avgCadence, Integer elevationChange) {

    // 러닝 종료 시 새로 만든다 — 아직 ID가 없다 (RunningRecord.finish()와 짝)
    public static SplitDraft create(int splitNumber, int avgPace, int distance, int duration,
                                    int routeStartIndex, int routeEndIndex,
                                    LocalDateTime startAt, LocalDateTime endAt,
                                    Integer calories, Integer avgCadence, Integer elevationChange) {
        return new SplitDraft(null, splitNumber, avgPace, distance, duration,
                routeStartIndex, routeEndIndex, startAt, endAt,
                calories, avgCadence, elevationChange);
    }
}
