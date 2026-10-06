package com.runiverse.running_service.unit_test.scheduling.application;

import com.runiverse.running_service.application.scheduling.command.recover.RecoverScheduledJobsHandler;
import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobCommand;
import com.runiverse.running_service.application.scheduling.port.in.RunScheduledJobUsecase;
import com.runiverse.running_service.application.scheduling.port.out.LoadPendingJobsPort;
import com.runiverse.running_service.application.scheduling.port.out.RegisterJobTimerPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import ch.qos.logback.classic.Level;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("부팅 시 예약 복구 단위 테스트")
class RecoverScheduledJobsHandlerTest {

    private static final long ROOM_ID = 125L;

    @Mock
    private LoadPendingJobsPort loadPendingJobsPort;

    @Mock
    private RegisterJobTimerPort registerJobTimerPort;

    @Mock
    private RunScheduledJobUsecase runScheduledJobUsecase;

    @InjectMocks
    private RecoverScheduledJobsHandler recoverScheduledJobsHandler;

    @Test
    @DisplayName("아직 안 온 예약은 타이머로 다시 건다")
    void registersFutureJobs() {
        // given -> 정본은 DB고 타이머는 재시작하면 사라지는 사본이다
        ScheduledJob future = job(1L, LocalDateTime.now().plusHours(2));
        given(loadPendingJobsPort.loadPending()).willReturn(List.of(future));

        // when
        recoverScheduledJobsHandler.recover();

        // then
        verify(registerJobTimerPort).register(future);
        verifyNoInteractions(runScheduledJobUsecase);
    }

    @Test
    @DisplayName("내려가 있는 동안 지나간 예약은 즉시 실행한다")
    void runsOverdueJobsImmediately() {
        // given -> 배포로 마감 시각을 놓친 방. 기다리면 시작 시각까지 넘긴다
        given(loadPendingJobsPort.loadPending())
                .willReturn(List.of(job(1L, LocalDateTime.now().minusMinutes(3))));

        // when
        recoverScheduledJobsHandler.recover();

        // then
        ArgumentCaptor<RunScheduledJobCommand> captor =
                ArgumentCaptor.forClass(RunScheduledJobCommand.class);
        verify(runScheduledJobUsecase).handle(captor.capture());
        assertThat(captor.getValue().scheduledJobId()).isEqualTo(1L);
        verifyNoInteractions(registerJobTimerPort);
    }

    @Test
    @DisplayName("한 건이 터져도 나머지 예약은 살린다")
    void keepsRecoveringWhenOneJobFails() {
        // given -> 순회가 끊기면 뒤 예약이 통째로 되살아나지 못한다
        ScheduledJob failing = job(1L, LocalDateTime.now().plusHours(2));
        ScheduledJob healthy = job(2L, LocalDateTime.now().plusHours(3));
        given(loadPendingJobsPort.loadPending()).willReturn(List.of(failing, healthy));
        willThrow(new IllegalStateException("등록 실패"))
                .given(registerJobTimerPort).register(failing);

        // when & then
        assertThatCode(() -> recoverScheduledJobsHandler.recover()).doesNotThrowAnyException();
        verify(registerJobTimerPort).register(healthy);
    }

    @Test
    @DisplayName("미실행 예약이 없으면 아무것도 하지 않는다")
    void doesNothingWhenNothingPending() {
        // given
        given(loadPendingJobsPort.loadPending()).willReturn(List.of());

        // when
        recoverScheduledJobsHandler.recover();

        // then
        verifyNoInteractions(registerJobTimerPort, runScheduledJobUsecase);
    }

    private static ScheduledJob job(long jobId, LocalDateTime executeAt) {
        return ScheduledJob.builder()
                .scheduledJobId(jobId)
                .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID))
                .executeAt(executeAt)
                .build();
    }

    @Test
    @DisplayName("복구 시작은 건수를 key=value로, 실패한 건은 예약 ID 값을 담아 ERROR로 남긴다")
    void logsRecoveryWithParsableValues() {
        // given
        LogCapture log = LogCapture.of(RecoverScheduledJobsHandler.class);
        ScheduledJob failing = job(1L, LocalDateTime.now().plusHours(2));
        ScheduledJob healthy = job(2L, LocalDateTime.now().plusHours(3));
        given(loadPendingJobsPort.loadPending()).willReturn(List.of(failing, healthy));
        willThrow(new IllegalStateException("등록 실패"))
                .given(registerJobTimerPort).register(failing);

        try {
            // when
            recoverScheduledJobsHandler.recover();

            // then -> "미실행 2건"이나 ScheduledJobId[value=1]은 수집 단계에서 값으로 읽히지 않는다
            assertThat(log.messages(Level.INFO)).containsExactly("[예약] 복구 시작 - pendingCount=2");
            assertThat(log.messages(Level.ERROR))
                    .containsExactly("[예약] 예약 복구 실패: 처리하지 못한 예외 - scheduledJobId=1");
            assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
        } finally {
            log.stop();
        }
    }
}
