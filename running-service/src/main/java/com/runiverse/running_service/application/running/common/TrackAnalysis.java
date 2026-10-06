package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.domain.running.record.SplitDraft;

import java.time.LocalDateTime;
import java.util.List;

public record TrackAnalysis(
        int totalDistanceMeters,
        int totalDurationSeconds,
        int avgPaceSecondsPerKm,
        Integer totalElevationGainMeters,   // 유효 표본이 부족하면 null
        String routePolyline,
        LocalDateTime startAt,
        LocalDateTime endAt,
        List<SplitDraft> splits
) {

}
