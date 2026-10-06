package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.ElevationChangeOutOfRangeException;

public record ElevationChange(int meters) {

    private static final int MIN = -10_000;
    private static final int MAX = 10_000;

    public ElevationChange {
        if (meters < MIN || meters > MAX) {
            throw new ElevationChangeOutOfRangeException();
        }
    }

    // 캐스트로 자르기 전에 long으로 묻는다 — 오버플로가 정상 범위로 래핑돼 검증을 우회하지 못하게
    public static boolean isValid(long meters) {
        return meters >= MIN && meters <= MAX;
    }
}
