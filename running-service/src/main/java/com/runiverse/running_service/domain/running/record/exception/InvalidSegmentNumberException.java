package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class InvalidSegmentNumberException extends BusinessException {

    public InvalidSegmentNumberException() {
        super(RunningRecordErrorCode.INVALID_SEGMENT_NUMBER);
    }
}
