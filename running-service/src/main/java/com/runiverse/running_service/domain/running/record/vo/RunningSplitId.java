package com.runiverse.running_service.domain.running.record.vo;

import com.runiverse.running_service.domain.running.record.exception.InvalidSplitIdException;

public record RunningSplitId(Long value) {

    public RunningSplitId {
        if (value == null || value < 1) {
            throw new InvalidSplitIdException();
        }
    }
}
