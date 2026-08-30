package com.runiverse.running_service.presentation.running.websocket.message;

import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.time.LocalDateTime;
import java.util.List;

public record RunningLocationUpdateRequest(List<Location> locations) {

    public boolean isValid() {
        return locations != null
                && !locations.isEmpty()
                && locations.stream().allMatch(Location::isValid);
    }

    public record Location(
            Long sequence,
            Double latitude,
            Double longitude,
            Double altitudeMeters,            // 단말이 못 재면 비어 온다(api-spec)
            Double accuracyMeters,
            Double speedMetersPerSecond,
            Double headingDegrees,
            Integer cadenceSpm,
            Integer currentPaceSecondsPerKm,
            LocalDateTime recordedAt
    ) {

        private static final double MAX_LATITUDE = 90.0;
        private static final double MAX_LONGITUDE = 180.0;

        // 거리 계산에 반드시 필요한 값만 막는다 - 배치 하나가 통째로 거절되면 그 10초가 통으로 빈다
        public boolean isValid() {
            return sequence != null && sequence >= 0 && sequence <= TrackPoint.MAX_SEQUENCE
                    && latitude != null && Math.abs(latitude) <= MAX_LATITUDE
                    && longitude != null && Math.abs(longitude) <= MAX_LONGITUDE
                    && accuracyMeters != null
                    && recordedAt != null;
        }
    }
}
