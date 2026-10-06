package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.SplitPaceOutOfRangeException;

// 구간 평균 페이스. Pace와 달리 "사람이 낼 수 있는 값" 범위를 두지 않는다 —
// 구간이 짧을수록 신호 대기·GPS 튐이 순간 속도를 그 범위 밖으로 밀어내는데,
// 구간 하나가 거부되면 기록 전체가 사라진다.
// 타당성 방어는 기록 전체 평균(Pace, 120~3600)이 그대로 맡는다.
public record SplitPace(int secondsPerKm) {

    private static final int MIN = 1;           // 0은 시간이 0인 구간 — ElapsedTime이 먼저 막는다
    private static final int MAX = 86_400_000;  // ElapsedTime 상한(24시간)을 1m 구간에 적용한 구조적 상한

    public SplitPace {
        if (secondsPerKm < MIN || secondsPerKm > MAX) {
            throw new SplitPaceOutOfRangeException();
        }
    }
}
