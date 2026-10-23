package com.runiverse.running_service.application.running.common;

import java.math.BigDecimal;

public final class CalorieCalculator {

    private static final double RESTING_VO2 = 3.5;          // mL/kg/분 — 서 있을 때의 산소 섭취량
    private static final double RUNNING_COEFFICIENT = 0.2;
    private static final double OXYGEN_ML_PER_KCAL = 200.0;   // 산소 1L가 약 5kcal
    private static final double SECONDS_PER_MINUTE = 60.0;
    private static final double METERS_PER_MINUTE_BASE = 60_000.0;   // 1km를 pace초에 → 분당 60000/pace 미터

    private CalorieCalculator() {
    }

    public static int kcal(int paceSecondsPerKm, int durationSeconds, BigDecimal weightKg) {
        return (int) Math.round(unroundedKcal(paceSecondsPerKm, durationSeconds, weightKg));
    }

    // 1kcal에 못 미치는 짧은 구간을 이어 붙일 때 누적한 뒤 반올림하도록 반올림 전 값을 낸다
    public static double unroundedKcal(int paceSecondsPerKm, int durationSeconds, BigDecimal weightKg) {
        double metersPerMinute = METERS_PER_MINUTE_BASE / paceSecondsPerKm;
        // 속도가 0에 가까워지면 서 있을 때의 섭취량만 남는다 — 멈춰 있던 구간에 clamp를 두지 않아도 되는 이유다
        double vo2 = RUNNING_COEFFICIENT * metersPerMinute + RESTING_VO2;   // mL/kg/분
        double minutes = durationSeconds / SECONDS_PER_MINUTE;
        return vo2 * weightKg.doubleValue() * minutes / OXYGEN_ML_PER_KCAL;
    }
}
