package com.runiverse.running_service.application.running.query.status;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;

// 활동 중이 아니면 방에 딸린 값이 모두 비고, 쿨다운만 남는다
public record GetUserStatusResult(
        UserRunningStatus status,
        RunningRoomType type,
        Long runningRoomId,
        LocalDateTime scheduledStartAt,
        Integer targetDistanceMeters,
        // 제재 이탈로 재신청이 막혀 있으면 해제 시각 — 없으면 null
        LocalDateTime cooldownUntil
) {

    public static GetUserStatusResult idle(LocalDateTime cooldownUntil) {
        return new GetUserStatusResult(
                UserRunningStatus.IDLE, null, null, null, null, cooldownUntil);
    }
}
