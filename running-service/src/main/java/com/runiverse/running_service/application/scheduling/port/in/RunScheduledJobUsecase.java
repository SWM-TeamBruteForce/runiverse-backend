package com.runiverse.running_service.application.scheduling.port.in;

import com.runiverse.running_service.application.scheduling.command.run.RunScheduledJobCommand;

public interface RunScheduledJobUsecase {

    // 메모리 타이머가 깨워서 부른다. 요청으로는 들어오지 않는다
    void handle(RunScheduledJobCommand command);
}
