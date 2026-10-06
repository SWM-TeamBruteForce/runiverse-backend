package com.runiverse.running_service.application.scheduling.port.out;

import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobId;

import java.util.Optional;

public interface LockScheduledJobPort {

    // 여러 인스턴스가 같은 예약을 타이머로 들고 있다 — 실행 직전에 잠그고 다시 읽어야
    // markSent()가 하나만 통과시킨다. 잠그지 않으면 둘 다 sent=false를 보고 함께 지나간다
    Optional<ScheduledJob> lockById(ScheduledJobId scheduledJobId);
}
