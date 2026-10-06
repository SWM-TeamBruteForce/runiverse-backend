package com.runiverse.running_service.domain.scheduling.vo;

import com.runiverse.running_service.domain.scheduling.exception.InvalidScheduledJobIdException;

public record ScheduledJobId(Long value) {

    public ScheduledJobId {
        if (value == null || value < 1) {
            throw new InvalidScheduledJobIdException();
        }
    }
}
