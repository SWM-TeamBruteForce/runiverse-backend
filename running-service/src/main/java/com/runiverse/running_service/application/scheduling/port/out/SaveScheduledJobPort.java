package com.runiverse.running_service.application.scheduling.port.out;

import com.runiverse.running_service.domain.scheduling.ScheduledJob;

public interface SaveScheduledJobPort {

    // 대상 생성과 같은 트랜잭션에서 부른다 — 커밋되면 예약도 함께 남는다.
    // 같은 (타입, 대상)이 이미 있으면 기존 예약을 그대로 돌려준다(UNIQUE가 행 중복을 막는다)
    ScheduledJob save(ScheduledJob job);
}
