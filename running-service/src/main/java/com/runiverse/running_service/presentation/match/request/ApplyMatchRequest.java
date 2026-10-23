package com.runiverse.running_service.presentation.match.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

// 선택지(시각·거리)는 운영값이라 여기서 보지 않는다 — ApplyMatchHandler가 MatchSlotPolicy로 본다
public record ApplyMatchRequest(
        @NotNull(message = "희망 시작 시각은 필수입니다.")
        LocalDateTime scheduledStartAt,
        @NotNull(message = "목표 거리는 필수입니다.")
        Integer targetDistanceMeters
) {

}
