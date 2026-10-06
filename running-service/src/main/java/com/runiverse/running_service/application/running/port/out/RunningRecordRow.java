package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;

// 기록 목록의 한 행 — 기록에 방 종류와 출발 인원을 붙인다
public record RunningRecordRow(
        Long runningRecordId,
        Long runningRoomId,
        RunningRoomType type,
        long playerCount,                   // 출발한 참가자 수 — 본인·탈퇴자 포함
        LocalDateTime startedAt,
        int totalDistanceMeters,
        int totalDurationSeconds,
        int averagePaceSecondsPerKm,
        Integer totalElevationGainMeters,   // 유효 표본이 부족하면 null
        String routePolyline
) {

}
