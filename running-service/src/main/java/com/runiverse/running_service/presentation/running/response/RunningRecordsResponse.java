package com.runiverse.running_service.presentation.running.response;

import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsResult;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;
import java.util.List;

public record RunningRecordsResponse(List<RunningRecordResponse> runningRecords) {

    public static RunningRecordsResponse from(GetMyRunningRecordsResult result) {
        return new RunningRecordsResponse(result.runningRecords().stream()
                .map(RunningRecordResponse::from)
                .toList());
    }

    public record RunningRecordResponse(
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
        static RunningRecordResponse from(GetMyRunningRecordsResult.RunningRecord record) {
            return new RunningRecordResponse(
                    record.runningRecordId(),
                    record.runningRoomId(),
                    record.type(),
                    record.playerCount(),
                    record.startedAt(),
                    record.totalDistanceMeters(),
                    record.totalDurationSeconds(),
                    record.averagePaceSecondsPerKm(),
                    record.totalElevationGainMeters(),
                    record.routePolyline());
        }
    }
}
