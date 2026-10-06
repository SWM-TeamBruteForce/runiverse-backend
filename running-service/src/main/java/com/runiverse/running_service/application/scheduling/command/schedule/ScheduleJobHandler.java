package com.runiverse.running_service.application.scheduling.command.schedule;

import com.runiverse.running_service.application.common.port.out.ScheduleJobPort;
import com.runiverse.running_service.application.scheduling.port.out.SaveScheduledJobPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 예약을 대상 생성과 같은 트랜잭션에 남기고, 타이머·전파는 커밋 뒤로 미룬다
@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleJobHandler implements ScheduleJobPort {

    private final SaveScheduledJobPort saveScheduledJobPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void schedule(ScheduledJobType type, Long targetId, LocalDateTime executeAt) {
        ScheduledJob job = saveScheduledJobPort.save(
                ScheduledJob.reserve(type, targetId, executeAt));
        // 커밋 전에 타이머를 걸거나 전파하면 롤백된 예약을 깨우게 된다
        eventPublisher.publishEvent(new ScheduledJobCreatedEvent(job));
    }
}
