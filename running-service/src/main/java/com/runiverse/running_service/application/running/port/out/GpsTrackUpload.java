package com.runiverse.running_service.application.running.port.out;

import java.time.LocalDateTime;
import java.util.UUID;

public record GpsTrackUpload(
        Long runningRoomId,
        UUID userId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String raw          // Redis 압축 포맷 그대로
) {

}
