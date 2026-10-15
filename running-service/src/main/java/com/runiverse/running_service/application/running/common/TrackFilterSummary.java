package com.runiverse.running_service.application.running.common;

// 로그·메트릭용 집계. 시계가 뒤로 간 칸이 없으면 단계별로 뺀 거리의 합이 원본 거리 − 필터 거리다
public record TrackFilterSummary(
        double rawMeters, double filteredMeters,
        int accuracyDroppedCount, double accuracyRemovedMeters,
        int spikePointCount, int jumpEdgeCount, double spikeRemovedMeters,
        int gapEdgeCount, int gapAcceptedCount, int gapRejectedCount,
        double gapAcceptedMeters, double gapRejectedMeters,
        double maxGapEdgeMeters, double maxGapEdgeSeconds,
        int pausedGapCount, double pausedGapRemovedMeters,
        int stopCount, double stopSeconds, double stopRemovedMeters,
        double maxStopSeconds, double maxStopDisplacementMeters,
        int radiusClampedCount
) {

}
