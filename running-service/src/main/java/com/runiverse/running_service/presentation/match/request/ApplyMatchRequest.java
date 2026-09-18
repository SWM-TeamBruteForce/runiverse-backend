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

    // TODO: 테스트용으로 창·간격 제한을 풀어둔 상태다. 운영 복귀 시 18:00 / 22:00 / 30으로 되돌린다
    private static final LocalTime EARLIEST = LocalTime.MIN;
    private static final LocalTime LATEST = LocalTime.MAX;
    private static final int SLOT_MINUTES = 1;
    private static final Set<Integer> ALLOWED_DISTANCES = Set.of(3_000, 5_000, 10_000);

    // null은 @NotNull이 따로 잡는다 — 여기서 또 걸면 같은 필드로 메시지가 두 번 나간다
    @AssertTrue(message = "시작 시각은 초·밀리초 없이 분 단위로만 선택할 수 있습니다.")
    public boolean isSlotAligned() {
        if (scheduledStartAt == null) {
            return true;
        }
        LocalTime time = scheduledStartAt.toLocalTime();
        // 초·나노는 계속 본다 — 01:54:30은 슬롯이 아닌데 분만 보면 통과한다
        return !time.isBefore(EARLIEST) && !time.isAfter(LATEST)
                && time.getMinute() % SLOT_MINUTES == 0
                && time.getSecond() == 0 && time.getNano() == 0;
    }

    @AssertTrue(message = "목표 거리는 3000, 5000, 10000 중 하나여야 합니다.")
    public boolean isAllowedDistance() {
        return targetDistanceMeters == null || ALLOWED_DISTANCES.contains(targetDistanceMeters);
    }
}
