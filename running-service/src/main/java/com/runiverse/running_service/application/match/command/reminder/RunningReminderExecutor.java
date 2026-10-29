package com.runiverse.running_service.application.match.command.reminder;

import com.runiverse.running_service.application.match.port.in.NotifyRunningReminderUsecase;
import com.runiverse.running_service.application.scheduling.ScheduledJobExecutor;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RunningReminderExecutor implements ScheduledJobExecutor {

    private final NotifyRunningReminderUsecase notifyRunningReminderUsecase;

    @Override
    public ScheduledJobType type() {
        return ScheduledJobType.RUNNING_REMINDER;
    }

    @Override
    public void execute(JobTarget target) {
        notifyRunningReminderUsecase.handle(new NotifyRunningReminderCommand(target.idAsLong()));
    }
}
