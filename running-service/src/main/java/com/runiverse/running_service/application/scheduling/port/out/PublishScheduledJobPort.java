package com.runiverse.running_service.application.scheduling.port.out;

import com.runiverse.running_service.domain.scheduling.ScheduledJob;

public interface PublishScheduledJobPort {

    // 다른 인스턴스도 타이머를 걸게 알린다 — 한 대만 들고 있으면
    // 그 대가 내려갈 때 아무도 깨지 않는다. 중복은 낭비가 아니라 이중화다
    void publish(ScheduledJob job);
}
