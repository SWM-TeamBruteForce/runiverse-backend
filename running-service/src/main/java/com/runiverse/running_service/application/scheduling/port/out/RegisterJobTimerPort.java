package com.runiverse.running_service.application.scheduling.port.out;

import com.runiverse.running_service.domain.scheduling.ScheduledJob;

public interface RegisterJobTimerPort {

    // 이 인스턴스 메모리에 executeAt 타이머를 건다 — 재시작하면 사라지는 사본이다.
    // 같은 예약이 부팅 복구·전파·본인 발행으로 여러 번 들어오므로 멱등이어야 한다
    void register(ScheduledJob job);
}
