package com.runiverse.running_service.presentation.running.websocket.message;

import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.time.LocalDateTime;
import java.util.List;

public record RunningLocationUpdateRequest(List<Location> locations) {

    public boolean isValid() {
        // 원소가 null이면 메서드 참조가 NPE로 터진다 — 이 검사는 핸들러 try 밖이라
        // 검증 실패 응답이 아니라 서버 오류로 연결이 끊긴다(1011). 잘못된 요청은 ERROR로 알리고 연결은 유지한다
        return locations != null
                && !locations.isEmpty()
                && locations.stream().allMatch(location -> location != null && location.isValid());
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
