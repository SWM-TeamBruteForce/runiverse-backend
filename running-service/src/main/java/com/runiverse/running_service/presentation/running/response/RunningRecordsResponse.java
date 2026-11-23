package com.runiverse.running_service.presentation.running.response;

import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsResult;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.presentation.recordcard.response.RecordCardDetailResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
            RecordCardResponse recordCard       // 붙은 기록 카드 — 없으면 null
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
                    record.recordCard() == null ? null : RecordCardResponse.from(record.recordCard()));
        }
    }

    // 기록에 붙은 카드 요약 — 사진이 있으면 thumbnailUrl, 없으면 routeArt(8-1과 같은 [x, y] 배열)
    public record RecordCardResponse(
            UUID recordCardId,
            String activityType,                // DAILY·TRAVEL·RACE — 8-1과 같은 값
            String thumbnailUrl,                // 사진 없는 카드면 null
            List<List<double[]>> routeArt       // 사진 있는 카드면 null, 경로가 없으면 빈 배열
    ) {

        static RecordCardResponse from(GetMyRunningRecordsResult.RecordCard card) {
            return new RecordCardResponse(card.recordCardId(), card.activityType().name(), card.thumbnailUrl(),
                    card.routeArt() == null ? null : RecordCardDetailResponse.routeArt(card.routeArt()));
        }
    }
}
