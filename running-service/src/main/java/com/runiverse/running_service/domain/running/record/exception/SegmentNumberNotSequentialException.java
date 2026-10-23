package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class SegmentNumberNotSequentialException extends BusinessException {

    public SegmentNumberNotSequentialException() {
        super(RunningRecordErrorCode.SEGMENT_NUMBER_NOT_SEQUENTIAL);
    }
}
