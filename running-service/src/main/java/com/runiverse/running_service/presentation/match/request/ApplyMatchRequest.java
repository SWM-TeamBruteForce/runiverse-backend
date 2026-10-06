package com.runiverse.running_service.presentation.match.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

public record ApplyMatchRequest(
        @NotNull(message = "희망 시작 시각은 필수입니다.")
        LocalDateTime scheduledStartAt,
        @NotNull(message = "목표 거리는 필수입니다.")
        Integer targetDistanceMeters
) {

    // 18:00 / 22:00 / 30분 단위로 신청 가능
    private static final LocalTime EARLIEST = LocalTime.of(18, 0);
    private static final LocalTime LATEST = LocalTime.of(22, 0);
    private static final int SLOT_MINUTES = 30;
    private static final Set<Integer> ALLOWED_DISTANCES = Set.of(3_000, 5_000, 10_000);

    // null은 @NotNull이 따로 잡는다 — 여기서 또 걸면 같은 필드로 메시지가 두 번 나간다
    @AssertTrue(message = "시작 시각은 18:00부터 22:00까지 30분 간격으로만 선택할 수 있습니다.")
    public boolean isSlotAligned() {
        if (scheduledStartAt == null) {
            return true;
        }
        LocalTime time = scheduledStartAt.toLocalTime();
        // 초·나노는 계속 본다 — 19:00:30은 슬롯이 아닌데 분만 보면 통과한다
        return !time.isBefore(EARLIEST) && !time.isAfter(LATEST)
                && time.getMinute() % SLOT_MINUTES == 0
                && time.getSecond() == 0 && time.getNano() == 0;
    }

    @AssertTrue(message = "목표 거리는 3000, 5000, 10000 중 하나여야 합니다.")
    public boolean isAllowedDistance() {
        return targetDistanceMeters == null || ALLOWED_DISTANCES.contains(targetDistanceMeters);
    }
}
