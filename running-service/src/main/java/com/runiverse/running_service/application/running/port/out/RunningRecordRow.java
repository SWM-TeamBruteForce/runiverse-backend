package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.recordcard.vo.RecordCardActivityType;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;
import java.util.UUID;

// 기록 목록의 한 행 — 기록에 방 종류와 출발 인원, 붙은 기록 카드를 붙인다
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
        UUID recordCardId,                  // 붙은 기록 카드 — 없으면 null
        String thumbnailImageKey,           // 카드의 썸네일 사진 key — 카드가 없거나 사진 없는 카드면 null
        RecordCardActivityType cardActivityType   // 카드의 활동 유형 — 카드가 없으면 null
) {

}
