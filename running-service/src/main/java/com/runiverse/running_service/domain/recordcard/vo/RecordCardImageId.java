package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.InvalidRecordCardImageIdException;

public record RecordCardImageId(Long value) {

    public RecordCardImageId {
        if (value == null || value < 1) {
            throw new InvalidRecordCardImageIdException();
        }
    }
}
