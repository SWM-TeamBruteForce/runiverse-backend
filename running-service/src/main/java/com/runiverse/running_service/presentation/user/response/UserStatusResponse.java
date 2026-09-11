package com.runiverse.running_service.presentation.user.response;

import com.runiverse.running_service.application.running.query.status.UserRunningStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;

import java.time.LocalDateTime;

public record UserStatusResponse(
        UserRunningStatus status,
        // 클라이언트가 채널을 고르는 기준 — SOLO는 스트림을 열지 않는다
        RunningRoomType type,
        Long runningRoomId,
        LocalDateTime scheduledStartAt,
        // 목표 없는 솔로 방은 null
        Integer targetDistanceMeters,
        // 제재 이탈로 재신청이 막혀 있으면 해제 시각
        LocalDateTime cooldownUntil
) {

}
