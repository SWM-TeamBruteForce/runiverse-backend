package com.runiverse.running_service.application.common.port.out;

import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;

import java.time.LocalDateTime;

// 정해진 시각에 무엇을 하도록 예약한다. 부르는 쪽은 저장·타이머·전파를 알지 않는다.
// 매칭 마감이 먼저 쓰고, 러닝 강제 종료·시작 리마인더가 같은 입구를 쓴다
public interface ScheduleJobPort {

    // 대상 생성과 같은 트랜잭션에서 부른다 — 대상이 롤백되면 예약도 함께 사라진다
    void schedule(ScheduledJobType type, Long targetId, LocalDateTime executeAt);
}
