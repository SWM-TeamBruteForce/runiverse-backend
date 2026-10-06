package com.runiverse.running_service.application.running.query.record;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;
import java.util.List;

public record GetMyRunningRecordsResult(List<RunningRecord> runningRecords) {

    public record RunningRecord(
            Long runningRecordId,
            Long runningRoomId,
            RunningRoomType type,
            long playerCount,
            LocalDateTime startedAt,
            int totalDistanceMeters,
            int totalDurationSeconds,
            int averagePaceSecondsPerKm,
            Integer totalElevationGainMeters,   // 유효 표본이 부족하면 null
            String routePolyline
    ) {

    }
}
