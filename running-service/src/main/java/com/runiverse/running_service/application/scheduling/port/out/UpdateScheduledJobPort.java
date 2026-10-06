package com.runiverse.running_service.application.scheduling.port.out;

import com.runiverse.running_service.domain.scheduling.ScheduledJob;

public interface UpdateScheduledJobPort {

    void update(ScheduledJob job);
}
