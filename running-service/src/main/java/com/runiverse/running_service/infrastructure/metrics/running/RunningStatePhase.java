package com.runiverse.running_service.infrastructure.metrics.running;

import java.util.Locale;

// 러닝 중 배치마다 도는 접근인가, 종료 때 한 번 도는 접근인가 —
// 같은 대상의 읽기라도 종료 때는 러닝 하나치를 통째로 읽어 분포가 전혀 다르다
public enum RunningStatePhase {
    RUNNING,
    FINISH;

    String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
