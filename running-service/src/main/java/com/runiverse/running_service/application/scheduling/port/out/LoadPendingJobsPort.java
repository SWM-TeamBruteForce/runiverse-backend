package com.runiverse.running_service.application.scheduling.port.out;


import com.runiverse.running_service.domain.scheduling.ScheduledJob;

import java.util.List;

public interface LoadPendingJobsPort {

    // 부팅 복구가 쓴다 — 아직 실행되지 않은 예약 전부.
    // 시각으로 거르지 않는다: 지난 것은 즉시 실행하고 남은 것은 타이머로 다시 걸어야 해서다
    List<ScheduledJob> loadPending();
}
