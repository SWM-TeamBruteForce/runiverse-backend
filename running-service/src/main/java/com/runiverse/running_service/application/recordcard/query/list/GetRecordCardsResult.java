package com.runiverse.running_service.application.recordcard.query.list;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 10-3 — URL은 presigned GET으로 바꾼 값이다. nextCursor가 null이면 마지막 페이지다
public record GetRecordCardsResult(List<Item> items, String nextCursor) {

    public GetRecordCardsResult {
        items = List.copyOf(items);
    }

    public record Item(
            UUID recordCardId,
            String thumbnailUrl,        // 대표 사진의 작은 버전 — 사진 없는 카드는 null
            int imageCount,
            LocalDateTime startedAt,
            int totalDistanceMeters
    ) {

    }
}
