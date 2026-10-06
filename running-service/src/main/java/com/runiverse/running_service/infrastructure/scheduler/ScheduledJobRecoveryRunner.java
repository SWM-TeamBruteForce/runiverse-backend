package com.runiverse.running_service.infrastructure.scheduler;

import com.runiverse.running_service.application.scheduling.port.in.RecoverScheduledJobsUsecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobRecoveryRunner {

    private final RecoverScheduledJobsUsecase recoverScheduledJobsUsecase;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        try {
            recoverScheduledJobsUsecase.recover();
        } catch (RuntimeException e) {
            // 복구가 실패해도 앱은 뜬다 — 신규 예약은 정상 동작하고,
            // 못 살린 예약은 다음 인스턴스가 뜰 때 다시 시도된다
            log.error("[예약] 부팅 복구 실패: 처리하지 못한 예외", e);
        }
    }
}
