package com.runiverse.running_service.application.recordcard.port.out;

import java.time.LocalDateTime;
import java.util.UUID;

// 목록(10-3)의 카드 한 장 — 경로·음악·회고 글은 싣지 않는다(상세 8-1에서 본다). key는 저장된 값 그대로다
public record RecordCardSummary(
        UUID recordCardId,
        String thumbnailImageKey,       // 대표 사진 원본 key — 사진이 없으면 null
        long imageCount,
        LocalDateTime startedAt,
        int totalDistanceMeters,
        int displayOrder                // 커서 — 순위 역순으로 읽는다
) {

}
