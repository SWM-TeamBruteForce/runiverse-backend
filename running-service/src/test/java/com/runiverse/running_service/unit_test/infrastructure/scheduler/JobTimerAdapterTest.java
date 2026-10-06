package com.runiverse.running_service.unit_test.infrastructure.scheduler;

import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobCommand;
import com.runiverse.running_service.application.scheduling.port.in.RunScheduledJobUsecase;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import com.runiverse.running_service.infrastructure.scheduler.JobTimerAdapter;
import com.runiverse.running_service.support.LogCapture;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("예약 타이머 어댑터 단위 테스트")
class JobTimerAdapterTest {

    private static final long JOB_ID = 3L;
    private static final long ROOM_ID = 125L;
    private static final LocalDateTime EXECUTE_AT = LocalDateTime.now().plusHours(2);

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private RunScheduledJobUsecase runScheduledJobUsecase;

    @Mock
    private ScheduledFuture<?> future;

    private JobTimerAdapter jobTimerAdapter;

    @BeforeEach
    void setUp() {
        jobTimerAdapter = new JobTimerAdapter(taskScheduler, runScheduledJobUsecase);
    }

    @Test
    @DisplayName("예약 시각에 깨우도록 건다")
    void schedulesAtExecuteAt() {
        // given -> 주기적으로 훑지 않고 그 시각에만 깬다
        givenSchedulable();

        // when
        jobTimerAdapter.register(job(JOB_ID));

        // then -> 저장 시각이 KST 벽시계라 JVM 기본 타임존으로 되돌린다
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(EXECUTE_AT.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("같은 예약이 여러 번 들어와도 한 번만 건다")
    void registersOnlyOncePerJob() {
        // given -> 부팅 복구·Redis 전파·본인 발행으로 같은 예약이 세 번 들어온다.
        //          인스턴스 사이의 중복은 잠금이 거르지만 안에서는 여기가 거른다
        givenSchedulable();

        // when
        jobTimerAdapter.register(job(JOB_ID));
        jobTimerAdapter.register(job(JOB_ID));
        jobTimerAdapter.register(job(JOB_ID));

        // then
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("다른 예약은 각각 건다")
    void registersEachJobSeparately() {
        // given
        givenSchedulable();

        // when
        jobTimerAdapter.register(job(JOB_ID));
        jobTimerAdapter.register(job(JOB_ID + 1));

        // then
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("깨어나면 예약 실행을 부른다")
    void runsJobWhenFired() {
        // given
        givenSchedulable();
        jobTimerAdapter.register(job(JOB_ID));

        // when -> 스케줄러가 그 시각에 하는 일을 대신 실행한다
        firedTask().run();

        // then
        verify(runScheduledJobUsecase).handle(new RunScheduledJobCommand(JOB_ID));
    }

    @Test
    @DisplayName("실행이 터져도 스케줄러 스레드로 예외를 올리지 않는다")
    void swallowsFailureOnFire() {
        // given -> 스케줄러 스레드로 올라가면 아무도 못 보고 조용히 사라진다
        givenSchedulable();
        jobTimerAdapter.register(job(JOB_ID));
        willThrow(new IllegalStateException("확정 실패"))
                .given(runScheduledJobUsecase).handle(any(RunScheduledJobCommand.class));

        // when & then
        assertThatCode(() -> firedTask().run()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실행이 터지면 예약 ID와 원인 예외를 담아 ERROR로 남긴다")
    void logsFailureOnFire() {
        // given -> 요청 스레드가 아니라 중앙 예외 핸들러가 없다. 여기서 안 찍으면 흔적이 없다
        LogCapture log = LogCapture.of(JobTimerAdapter.class);
        givenSchedulable();
        jobTimerAdapter.register(job(JOB_ID));
        willThrow(new IllegalStateException("확정 실패"))
                .given(runScheduledJobUsecase).handle(any(RunScheduledJobCommand.class));

        try {
            // when
            firedTask().run();

            // then
            assertThat(log.messages(Level.ERROR))
                    .containsExactly("[예약] 예약 실행 실패: 처리하지 못한 예외 - scheduledJobId=" + JOB_ID);
            assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy().getClassName())
                    .isEqualTo(IllegalStateException.class.getName());
        } finally {
            log.stop();
        }
    }

    @Test
    @DisplayName("저장되지 않은 예약은 걸 수 없다")
    void rejectsUnsavedJob() {
        // given -> 타이머는 ID로 실행을 부른다. ID가 없으면 깨워도 찾을 수 없다
        ScheduledJob unsaved =
                ScheduledJob.reserve(ScheduledJobType.MATCH_CLOSE, ROOM_ID, EXECUTE_AT);

        // when & then
        assertThatThrownBy(() -> jobTimerAdapter.register(unsaved))
                .isInstanceOf(IllegalStateException.class);
    }

    private void givenSchedulable() {
        // computeIfAbsent는 null을 담지 않는다 — 실제 TaskScheduler도 future를 돌려준다
        given(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .willAnswer(invocation -> future);
    }

    private Runnable firedTask() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(captor.capture(), any(Instant.class));
        return captor.getValue();
    }

    private static ScheduledJob job(long jobId) {
        return ScheduledJob.builder()
                .scheduledJobId(jobId)
                .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID))
                .executeAt(EXECUTE_AT)
                .build();
    }
}
