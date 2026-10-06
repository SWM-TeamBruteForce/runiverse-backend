package com.runiverse.running_service.application.running.common;

import java.math.BigDecimal;

public final class CalorieCalculator {

    private static final double RESTING_VO2 = 3.5;          // mL/kg/분 — 이 값이 곧 1 MET
    private static final double RUNNING_COEFFICIENT = 0.2;
    private static final double SECONDS_PER_HOUR = 3_600.0;
    private static final double METERS_PER_MINUTE_BASE = 60_000.0;   // 1km를 pace초에 → 분당 60000/pace 미터

    private CalorieCalculator() {
    }

    public static int kcal(int paceSecondsPerKm, int durationSeconds, BigDecimal weightKg) {
        double metersPerMinute = METERS_PER_MINUTE_BASE / paceSecondsPerKm;
        // 속도가 0에 가까워지면 MET가 1로 수렴한다 — 멈춰 있던 구간에 clamp를 두지 않아도 되는 이유다
        double met = (RUNNING_COEFFICIENT * metersPerMinute + RESTING_VO2) / RESTING_VO2;
        return (int) Math.round(met * weightKg.doubleValue() * (durationSeconds / SECONDS_PER_HOUR));
    }
}
