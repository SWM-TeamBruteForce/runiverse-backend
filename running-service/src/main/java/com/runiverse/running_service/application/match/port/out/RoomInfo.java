package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RoomInfo(
        Long runningRoomId,
        RunningRoomStatus status,
        LocalDateTime scheduledStartAt,
        // 모집 마감 시각 — start_at - 운영 오프셋으로 계산한다.
        // running_rooms.close_at(방이 실제로 닫힌 시각)과 이름이 겹치니 주의
        LocalDateTime closeAt,
        Integer targetDistanceMeters,
        // 참가자가 없으면 null — 평균 낼 대상이 없다
        Integer teamAveragePaceSecondsPerKm,
        List<RoomPlayer> players
) {

    public record RoomPlayer(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String introduction,
            int averagePaceSecondsPerKm,
            // 탈퇴하면 users 행이 지워져 프로필 조회에서 빠진다
            boolean isDeleted
    ) {

    }
}
