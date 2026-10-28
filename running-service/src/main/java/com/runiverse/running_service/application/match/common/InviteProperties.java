package com.runiverse.running_service.application.match.common;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "invite")
@Validated
public record InviteProperties(
        // 시작 시각 하한 = 지금 + 이 값. 상한은 오늘 23:59로 고정이라 설정이 아니다
        @NotNull Duration minLeadTime,
        // 모집 마감 = start_at - 이 값. 매칭의 close-offset과 따로 둔다 —
        // 친구끼리는 인원을 미리 굳힐 이유가 없어 시작 직전까지 받는다.
        // 시작 통지(match.ready-offset)보다 커야 하는 조건은 두 설정에 걸쳐 있어 시작 시각 정책이 본다
        @NotNull Duration closeOffset,
        // 목표 거리는 자유 입력이라 선택지가 아니라 범위로 막는다.
        // Distance VO 상한(500km)은 저장 가능한 값의 한계라 입력 범위와 다르다
        @NotNull @Positive Integer minDistanceMeters,
        @NotNull @Positive Integer maxDistanceMeters,
        // 한 보낸 사람이 한 방에서 한 사람에게 보낼 수 있는 횟수 — 닿으면 그 사람에게는 더 보낼 수 없다.
        // 거절·나감 뒤에도 이어서 센다 — 거절당한 뒤 계속 보내는 것을 막는 게 목적이다
        @NotNull @Positive Integer maxInviteCount
) {

    // null 검사는 @NotNull이 따로 보고한다 — 여기서 터지면 원인이 가려진다
    @AssertTrue(message = "초대 마감 오프셋은 0보다 커야 한다")
    public boolean isCloseOffsetPositive() {
        return closeOffset == null || closeOffset.isPositive();
    }

    // 같거나 작으면 방이 만들어지는 순간 이미 마감이라 초대를 보낼 틈이 없다
    @AssertTrue(message = "최소 준비 시간은 초대 마감 오프셋보다 길어야 한다")
    public boolean isLeadTimeLongerThanCloseOffset() {
        return minLeadTime == null || closeOffset == null || minLeadTime.compareTo(closeOffset) > 0;
    }

    @AssertTrue(message = "최소 거리는 최대 거리보다 클 수 없다")
    public boolean isDistanceRangeOrdered() {
        return minDistanceMeters == null || maxDistanceMeters == null
                || minDistanceMeters <= maxDistanceMeters;
    }
}
