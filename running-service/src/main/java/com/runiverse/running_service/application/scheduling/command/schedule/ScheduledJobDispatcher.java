package com.runiverse.running_service.application.scheduling.command.schedule;

import com.runiverse.running_service.application.scheduling.port.out.PublishScheduledJobPort;
import com.runiverse.running_service.application.scheduling.port.out.RegisterJobTimerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// MatchEventDispatcher와 같은 자리 — 커밋된 뒤에만 밖으로 나간다
@Component
@RequiredArgsConstructor
public class ScheduledJobDispatcher {

    private final RegisterJobTimerPort registerJobTimerPort;
    private final PublishScheduledJobPort publishScheduledJobPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ScheduledJobCreatedEvent event) {
        // 내 것을 먼저 건다 — Redis가 죽어 있어도 최소한 한 대는 깬다
        registerJobTimerPort.register(event.job());
        publishScheduledJobPort.publish(event.job());
    }
}
