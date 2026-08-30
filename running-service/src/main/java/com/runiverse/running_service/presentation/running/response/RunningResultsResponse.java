package com.runiverse.running_service.presentation.running.response;

import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RunningResultsResponse(
        Long runningRoomId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<double[]> routes,
        List<PlayerResponse> players
) {

    public static RunningResultsResponse from(GetRunningResultsResult result) {
        return new RunningResultsResponse(
                result.runningRoomId(),
                result.startedAt(),
                result.finishedAt(),
                routes(result.routes()),
                result.players().stream().map(PlayerResponse::from).toList());
    }

    // [위도, 경도] 두 칸 배열로 내린다 — 점마다 키 이름을 반복하지 않는다(api-spec §0).
    // GeoJSON은 경도가 먼저라 반대이므로 뒤집지 않는다
    private static List<double[]> routes(List<RoutePoint> points) {
        return points == null
                ? null
                : points.stream()
                .map(point -> new double[]{point.latitude(), point.longitude()})
                .toList();
    }

    public record PlayerResponse(
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

        static PlayerResponse from(GetRunningResultsResult.Player player) {
            return new PlayerResponse(
                    player.userId(),
                    player.nickname(),
                    player.profileImageUrl(),
                    player.status(),
                    player.isDeleted(),
                    player.isMe(),
                    player.totalDistanceMeters(),
                    player.totalDurationSeconds(),
                    player.totalCaloriesKcal(),
                    player.averagePaceSecondsPerKm(),
                    player.averageCadenceSpm(),
                    player.totalElevationGainMeters());
        }
    }
}
