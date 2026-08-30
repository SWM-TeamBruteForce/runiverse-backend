package com.runiverse.running_service.application.running.query.split;

import com.runiverse.running_service.application.running.port.out.RoutePoint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record GetRunningSplitResultsResult(
        Long runningRoomId,
        // 방 전체가 공유하는 고정 구간 거리 — 운영 설정에서 그대로 나간다
        int splitDistanceMeters,
        // 아래 넷은 조회하는 본인 기록 기준이다 — 본인 기록이 없으면 null(api-spec 6-2)
        Integer totalDistanceMeters,
        Integer totalElevationGainMeters,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        // 참가자 메타데이터는 여기 한 번만 — 구간마다 반복하면 응답이 MB 단위가 된다
        List<Player> players,
        List<Split> splits
) {

    public record Player(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String status,
            boolean isDeleted,
            boolean isMe
    ) {

    }

    public record Split(
            int splitNumber,
            int startDistanceMeters,
            int endDistanceMeters,
            int distanceMeters,
            // 이 구간의 본인 경로 — 같은 객체의 players가 전원인 것과 다르다(api-spec 6-2)
            List<RoutePoint> routes,
            List<SplitPlayer> players
    ) {

    }

    // userId만 두고 나머지는 최상위 players와 조인한다 — 닉네임·presigned URL을 구간마다 싣지 않는다
    public record SplitPlayer(
            UUID userId,
            int durationSeconds,
            int averagePaceSecondsPerKm,
            Integer averageCadenceSpm,
            int caloriesKcal,
            Integer elevationChangeMeters
    ) {

    }
}
