package com.runiverse.running_service.unit_test.infrastructure.scheduler;

import ch.qos.logback.classic.Level;
import com.runiverse.running_service.application.scheduling.port.in.RecoverScheduledJobsUsecase;
import com.runiverse.running_service.infrastructure.scheduler.ScheduledJobRecoveryRunner;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("부팅 예약 복구 러너 단위 테스트")
class ScheduledJobRecoveryRunnerTest {

    @Mock
    private RecoverScheduledJobsUsecase recoverScheduledJobsUsecase;

    @InjectMocks
    private ScheduledJobRecoveryRunner runner;

    @Test
    @DisplayName("복구가 실패해도 앱 기동을 막지 않고 원인 예외를 담아 [예약] ERROR로 남긴다")
    void logsFailureWithoutBlockingStartup() {
        // given -> 신규 예약은 정상 동작하고, 못 살린 예약은 다음 인스턴스가 뜰 때 다시 시도된다
        LogCapture log = LogCapture.of(ScheduledJobRecoveryRunner.class);
        doThrow(new IllegalStateException("DB 오류"))
                .when(recoverScheduledJobsUsecase).recover();

        try {
            // when
            assertThatCode(() -> runner.recoverOnStartup()).doesNotThrowAnyException();

            // then
            assertThat(log.messages(Level.ERROR))
                    .containsExactly("[예약] 부팅 복구 실패: 처리하지 못한 예외");
            assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
        } finally {
            log.stop();
        }
    }
}
