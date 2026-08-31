package com.runiverse.running_service.application.running.command.finish;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "running-finish")
@Validated
public record RunningFinishProperties(
        // 확정 거리 ÷ 목표 거리가 이 값 미만이면 RUNNING_LEFT_PENALTY
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double penaltyDistanceRatio,
        @NotNull @Positive Integer splitDistanceMeters,
        // 둘 중 하나라도 못 넘기면 기록 없이 상태만 확정한다
        @NotNull @Positive Integer minDistanceMeters,
        @NotNull @Positive Integer minDurationSeconds,
        // 이 값 이하의 고도 변화는 GPS 수직 오차로 보고 버린다
        @NotNull @Positive Double elevationNoiseThresholdMeters
) {

}
