package com.runiverse.running_service.application.running.query.result;

import com.runiverse.running_service.application.running.port.out.RoutePoint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record GetRunningResultsResult(
        Long runningRoomId,
        // 아래 셋은 조회하는 본인 기록 기준이다 — 본인 기록이 없으면 null(api-spec 6-1)
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        // 저장은 encoded polyline이지만 응답은 좌표 배열이다.
        // 시작·끝 지점은 이 목록의 첫 원소·끝 원소라 따로 싣지 않는다
        List<RoutePoint> routes,
        List<Player> players
) {

    // 기록이 없는 참가자는 status 아래가 전부 null이다 — 화면이 "기록 없음"으로 표시한다
    public record Player(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String status,
            boolean isDeleted,
            boolean isMe,
            Integer totalDistanceMeters,
            Integer totalDurationSeconds,
            Integer totalCaloriesKcal,
            Integer averagePaceSecondsPerKm,
            Integer averageCadenceSpm,
            Integer totalElevationGainMeters
    ) {

    }
}
