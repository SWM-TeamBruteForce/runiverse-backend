package com.runiverse.running_service.presentation.match.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// 분 단위·거리 범위는 운영값이라 여기서 보지 않는다 — OpenInviteRoomHandler가 InviteStartPolicy로 본다
public record OpenInviteRoomRequest(
        @NotNull(message = "시작 시각은 필수입니다.")
        LocalDateTime scheduledStartAt,
        @NotNull(message = "목표 거리는 필수입니다.")
        Integer targetDistanceMeters
) {

}
