package com.runiverse.running_service.infrastructure.scheduler;

import com.runiverse.running_service.application.user.port.in.RedactDeletedUsersUsecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeletedUserRedactionRunner {

    private final RedactDeletedUsersUsecase redactDeletedUsersUsecase;

    // 크론 해석을 JVM 기본 시간대에 기대지 않고 앱 시간대로 명시한다
    @Scheduled(cron = "${account-deletion.redaction-cron}", zone = "${app.time-zone}")
    public void redactOnSchedule() {
        try {
            redactDeletedUsersUsecase.redactAfterRetention();
        } catch (RuntimeException e) {
            // 배치가 죽어도 앱은 계속 돈다 — 못 지운 건은 다음 실행에 다시 걸린다
            log.error("[회원] 탈퇴 기록 신원 정보 제거 실패: 처리하지 못한 예외", e);
        }
    }
}
