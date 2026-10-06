package com.runiverse.running_service.application.scheduling;

import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;

// 타입별로 "그 시각에 무엇을 할지"를 구현한다.
// 예약 실행기는 선점까지만 책임지고 무엇을 하는지는 모른다 —
// 타입이 늘어도 실행기는 그대로고 이 구현만 늘어난다
public interface ScheduledJobExecutor {

    ScheduledJobType type();

    void execute(JobTarget target);
}
