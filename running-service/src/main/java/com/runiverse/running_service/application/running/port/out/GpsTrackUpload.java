package com.runiverse.running_service.application.running.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record GpsTrackUpload(
        Long runningRoomId,
        UUID userId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String raw,         // Redis 압축 포맷 그대로
        List<Long> pausedSequences  // 일시정지 표시 — 원본으로 다시 계산할 때 트랙 필터에 넘긴다
) {

}
