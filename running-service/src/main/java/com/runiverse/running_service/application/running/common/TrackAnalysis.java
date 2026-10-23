package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.domain.running.record.SegmentDraft;
import com.runiverse.running_service.domain.running.record.SplitDraft;

import java.time.LocalDateTime;
import java.util.List;

public record TrackAnalysis(
        int totalDistanceMeters,
        int totalDurationSeconds,
        int avgPaceSecondsPerKm,
        Integer totalElevationGainMeters,   // 유효 표본이 부족하면 null
        // 끊긴 곳에서 나뉜 경로 조각 — 조각 번호 순이고, 이으면 10m 경계점 전체다
        List<SegmentDraft> segments,
        LocalDateTime startAt,
        LocalDateTime endAt,
        List<SplitDraft> splits
) {

}
