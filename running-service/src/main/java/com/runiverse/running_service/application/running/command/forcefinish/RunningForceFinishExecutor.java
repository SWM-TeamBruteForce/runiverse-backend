package com.runiverse.running_service.application.running.command.forcefinish;

import com.runiverse.running_service.application.running.port.in.ForceFinishRunningRoomUsecase;
import com.runiverse.running_service.application.scheduling.ScheduledJobExecutor;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RunningForceFinishExecutor implements ScheduledJobExecutor {

    private final ForceFinishRunningRoomUsecase forceFinishRunningRoomUsecase;

    @Override
    public ScheduledJobType type() {
        return ScheduledJobType.RUNNING_FORCE_FINISH;
    }

    @Override
    public void execute(JobTarget target) {
        forceFinishRunningRoomUsecase.handle(
                new ForceFinishRunningRoomCommand(target.idAsLong()));
    }
}
