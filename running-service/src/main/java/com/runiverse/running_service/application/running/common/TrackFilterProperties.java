package com.runiverse.running_service.application.running.common;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "running-track-filter")
@Validated
public record TrackFilterProperties(
        // 정확도(m)가 이보다 나쁜 좌표는 계산에서 뺀다
        @NotNull @Positive Double maxAccuracyMeters,
        // 사람이 낼 수 없는 칸 속도 — 넘으면 GPS 튐
        @NotNull @Positive Double maxSpeedMetersPerSecond,
        // 정지 반경은 좌표 정확도를 이 둘 사이로 자른 값이다
        @NotNull @Positive Double stopMinRadiusMeters,
        @NotNull @Positive Double stopMaxRadiusMeters,
        @NotNull @Positive Integer stopMinDurationSeconds,
        // 수집 간격이 이보다 길면 중간을 보지 못한 칸이다
        @NotNull @Positive Integer gapMinSeconds,
        @NotNull @Positive Double gapMaxDistanceMeters,
        @NotNull @Positive Double gapMinSpeedMetersPerSecond,
        @NotNull @DecimalMin("1.0") Double gapSpeedToleranceRatio,
        @NotNull @Positive Integer gapReferenceWindowSeconds,
        // 출발 직후라 비교할 직전 속도가 없을 때의 상한
        @NotNull @Positive Double gapFallbackMaxSpeedMetersPerSecond
) {

    // null 검사는 @NotNull이 따로 보고한다 — 여기서 터지면 원인이 가려진다
    @AssertTrue(message = "정지 최대 반경은 최소 반경 이상이어야 한다")
    public boolean isStopRadiusOrdered() {
        return stopMinRadiusMeters == null || stopMaxRadiusMeters == null
                || stopMaxRadiusMeters >= stopMinRadiusMeters;
    }

    // 반경 안에 최소 시간 머물렀다는 것은 그동안 평균 속도가 최대 반경 ÷ 최소 시간 이하였다는 뜻이다.
    // 이 값이 걷기 하한보다 크면 한 방향으로 걷는 사람도 정지로 잡힌다
    @AssertTrue(message = "정지로 잡히는 속도가 관측 안 된 칸의 속도 하한보다 느려야 한다")
    public boolean isStopSlowerThanWalking() {
        return stopMaxRadiusMeters == null || stopMinDurationSeconds == null
                || gapMinSpeedMetersPerSecond == null
                || stopMaxRadiusMeters / stopMinDurationSeconds < gapMinSpeedMetersPerSecond;
    }

    // 반경 안에서 비었다가 이어진 칸(제자리 일시정지)은 그 칸 하나로 최소 시간을 넘겨 항상 정지가 된다
    @AssertTrue(message = "관측 안 된 칸 문턱은 정지 최소 시간 이상이어야 한다")
    public boolean isGapLongerThanStop() {
        return gapMinSeconds == null || stopMinDurationSeconds == null
                || gapMinSeconds >= stopMinDurationSeconds;
    }
}
