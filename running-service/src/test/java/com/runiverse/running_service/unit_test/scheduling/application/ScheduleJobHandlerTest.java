package com.runiverse.running_service.unit_test.scheduling.application;

import com.runiverse.running_service.application.scheduling.command.schedule.ScheduleJobHandler;
import com.runiverse.running_service.application.scheduling.command.schedule.ScheduledJobCreatedEvent;
import com.runiverse.running_service.application.scheduling.port.out.SaveScheduledJobPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("예약 등록 단위 테스트")
class ScheduleJobHandlerTest {

    private static final long JOB_ID = 3L;
    private static final long ROOM_ID = 125L;
    private static final LocalDateTime EXECUTE_AT = LocalDateTime.of(2026, 9, 6, 19, 50);

    @Mock
    private SaveScheduledJobPort saveScheduledJobPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ScheduleJobHandler scheduleJobHandler;

    @Test
    @DisplayName("대상과 시각을 담아 예약을 저장한다")
    void savesReservation() {
        // given
        given(saveScheduledJobPort.save(any(ScheduledJob.class))).willReturn(storedJob());

        // when
        scheduleJobHandler.schedule(ScheduledJobType.MATCH_CLOSE, ROOM_ID, EXECUTE_AT);

        // then -> 대상 생성과 같은 트랜잭션이라 방이 롤백되면 예약도 함께 사라진다
        ArgumentCaptor<ScheduledJob> captor = ArgumentCaptor.forClass(ScheduledJob.class);
        verify(saveScheduledJobPort).save(captor.capture());
        assertThat(captor.getValue().isNew()).isTrue();
        assertThat(captor.getValue().getTarget())
                .isEqualTo(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID));
        assertThat(captor.getValue().getExecuteAt()).isEqualTo(EXECUTE_AT);
    }

    @Test
    @DisplayName("타이머와 전파는 커밋 뒤로 미룬다")
    void defersTimerAndPropagationUntilCommit() {
        // given -> 커밋 전에 걸면 롤백된 예약을 깨우게 된다
        given(saveScheduledJobPort.save(any(ScheduledJob.class))).willReturn(storedJob());

        // when
        scheduleJobHandler.schedule(ScheduledJobType.MATCH_CLOSE, ROOM_ID, EXECUTE_AT);

        // then -> 저장된 예약을 그대로 실어 보낸다. 타이머를 걸려면 ID가 필요하다
        ArgumentCaptor<ScheduledJobCreatedEvent> captor =
                ArgumentCaptor.forClass(ScheduledJobCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().job().getScheduledJobId()).isPresent();
        assertThat(captor.getValue().job().getExecuteAt()).isEqualTo(EXECUTE_AT);
    }

    private static ScheduledJob storedJob() {
        return ScheduledJob.builder()
                .scheduledJobId(JOB_ID)
                .target(JobTarget.of(ScheduledJobType.MATCH_CLOSE, ROOM_ID))
                .executeAt(EXECUTE_AT)
                .build();
    }
}
