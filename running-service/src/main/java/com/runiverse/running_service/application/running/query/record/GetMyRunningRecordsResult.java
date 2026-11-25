package com.runiverse.running_service.application.running.query.record;

import com.runiverse.running_service.application.recordcard.common.RouteArtConverter;
import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
            Integer totalElevationGainMeters, // 유효 표본이 부족하면 null
            RecordCard recordCard             // 붙은 기록 카드 — 없으면 null
    ) {

    }

    // 달력·작성 화면용 카드 요약 — 사진이 있으면 썸네일 URL, 없으면 선 아트(경로가 없으면 빈 목록)
    public record RecordCard(
            UUID recordCardId,
            RecordCardActivityType activityType,
            String thumbnailUrl,                          // 사진 없는 카드면 null
            List<List<RouteArtConverter.Point>> routeArt  // 사진 있는 카드면 null
    ) {

    }
}
