package com.runiverse.running_service.presentation.recordcard.response;

import com.runiverse.running_service.application.recordcard.query.list.GetRecordCardsResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 10-3 — 공통 커서 규칙(items · nextCursor). 마지막 페이지면 nextCursor는 null
public record RecordCardsResponse(List<ItemResponse> items, String nextCursor) {

    public static RecordCardsResponse from(GetRecordCardsResult result) {
        return new RecordCardsResponse(result.items().stream().map(ItemResponse::from).toList(), result.nextCursor());
    }

    public record ItemResponse(
            UUID recordCardId,
            String thumbnailUrl,        // 대표 사진의 작은 버전 — 사진 없는 카드는 null
            int imageCount,
            LocalDateTime startedAt,
            int totalDistanceMeters
    ) {

        static ItemResponse from(GetRecordCardsResult.Item item) {
            return new ItemResponse(item.recordCardId(), item.thumbnailUrl(), item.imageCount(), item.startedAt(),
                    item.totalDistanceMeters());
        }
    }
}
