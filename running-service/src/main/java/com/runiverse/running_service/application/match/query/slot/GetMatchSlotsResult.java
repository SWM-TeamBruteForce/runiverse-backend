package com.runiverse.running_service.application.match.query.slot;

import java.time.LocalDateTime;
import java.util.List;

// slots는 scheduledStartAt 오름차순이다
public record GetMatchSlotsResult(
        List<MatchSlot> slots
) {

    public record MatchSlot(
            LocalDateTime scheduledStartAt,
            int waitingCount,
            boolean selectable  // 모집 마감 전이면 true — 신청 시 MATCH_SLOT_CLOSED 판정과 같은 경계
    ) {

    }
}
