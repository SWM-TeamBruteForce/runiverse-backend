package com.runiverse.running_service.domain.common.vo;

import com.runiverse.running_service.domain.common.exception.InvalidRunningRecordIdException;

public record RunningRecordId(Long value) {

    public RunningRecordId {
        if (value == null || value < 1) {
            throw new InvalidRunningRecordIdException();
        }
    }
}
