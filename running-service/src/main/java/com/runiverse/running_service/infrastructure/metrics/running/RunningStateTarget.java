package com.runiverse.running_service.infrastructure.metrics.running;

import java.util.Locale;

// 포트 하나가 대상 하나다 — 저장소를 바꿔도 같은 값을 써야 두 구현이 같은 칸에 찍힌다
public enum RunningStateTarget {
    TRACK,
    DISTANCE,
    STATUS,
    COMBO_SNAPSHOT,
    COMBO_PAIR,
    COMBO_SEGMENT;

    String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
