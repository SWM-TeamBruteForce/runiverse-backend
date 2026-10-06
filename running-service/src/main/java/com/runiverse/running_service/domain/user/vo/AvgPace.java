package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.AvgPaceOutOfRangeException;

public record AvgPace(int secondPerKm) {

    private static final int MIN = 120; // 최소 페이스
    private static final int MAX = 1800; // 최대 페이스

    public AvgPace {
        if (secondPerKm < MIN || secondPerKm > MAX) {
            throw new AvgPaceOutOfRangeException();
        }
    }

    // 러닝 기록에서 낸 평균을 온보딩 페이스로 옮길 때 쓴다.
    // 기록 쪽 Pace는 3600까지 허용해(걷기 구간) 그대로는 이 범위를 넘길 수 있다 —
    // 갱신을 포기하는 대신 경계로 맞춘다
    public static AvgPace clamped(int secondPerKm) {
        return new AvgPace(Math.min(MAX, Math.max(MIN, secondPerKm)));
    }
}
