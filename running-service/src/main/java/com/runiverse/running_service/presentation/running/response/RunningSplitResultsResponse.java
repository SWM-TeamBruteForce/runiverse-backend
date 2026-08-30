package com.runiverse.running_service.presentation.running.response;

import com.runiverse.running_service.application.running.port.out.RoutePoint;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RunningSplitResultsResponse(
        Long runningRoomId,
        int splitDistanceMeters,
        Integer totalDistanceMeters,
        Integer totalElevationGainMeters,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<PlayerResponse> players,
        List<SplitResponse> splits
) {

    public static RunningSplitResultsResponse from(GetRunningSplitResultsResult result) {
        return new RunningSplitResultsResponse(
                result.runningRoomId(),
                result.splitDistanceMeters(),
                result.totalDistanceMeters(),
                result.totalElevationGainMeters(),
                result.startedAt(),
                result.finishedAt(),
                result.players().stream().map(PlayerResponse::from).toList(),
                result.splits().stream().map(SplitResponse::from).toList());
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
            boolean isMe
    ) {

        static PlayerResponse from(GetRunningSplitResultsResult.Player player) {
            return new PlayerResponse(
                    player.userId(),
                    player.nickname(),
                    player.profileImageUrl(),
                    player.status(),
                    player.isDeleted(),
                    player.isMe());
        }
    }

    public record SplitResponse(
            int splitNumber,
            int startDistanceMeters,
            int endDistanceMeters,
            int distanceMeters,
            List<double[]> routes,
            List<SplitPlayerResponse> players
    ) {

        static SplitResponse from(GetRunningSplitResultsResult.Split split) {
            return new SplitResponse(
                    split.splitNumber(),
                    split.startDistanceMeters(),
                    split.endDistanceMeters(),
                    split.distanceMeters(),
                    RunningSplitResultsResponse.routes(split.routes()),
                    split.players().stream().map(SplitPlayerResponse::from).toList());
        }
    }

    public record SplitPlayerResponse(
            UUID userId,
            int durationSeconds,
            int averagePaceSecondsPerKm,
            Integer averageCadenceSpm,
            int caloriesKcal,
            Integer elevationChangeMeters
    ) {

        static SplitPlayerResponse from(GetRunningSplitResultsResult.SplitPlayer player) {
            return new SplitPlayerResponse(
                    player.userId(),
                    player.durationSeconds(),
                    player.averagePaceSecondsPerKm(),
                    player.averageCadenceSpm(),
                    player.caloriesKcal(),
                    player.elevationChangeMeters());
        }
    }
}
