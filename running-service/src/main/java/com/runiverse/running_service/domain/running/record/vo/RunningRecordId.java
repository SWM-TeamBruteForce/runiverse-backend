package com.runiverse.running_service.domain.running.record.vo;

import com.runiverse.running_service.domain.running.record.exception.InvalidRunningRecordIdException;

public record RunningRecordId(Long value) {

    public RunningRecordId {
        if (value == null || value < 1) {
            throw new InvalidRunningRecordIdException();
        }
    }
}
