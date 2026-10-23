package com.runiverse.running_service.infrastructure.metrics.running;

import java.util.Locale;

// 러닝 중 상태를 어디에 두었나 — 지금은 Redis뿐이고, DB 직행 버전을 부하 테스트할 때 db가 붙는다.
// 같은 메트릭 이름 아래 store만 달리 찍혀야 두 구현을 한 그래프에서 비교할 수 있다
public enum RunningStateStore {
    REDIS,
    DB;

    String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
