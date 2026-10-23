package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class SplitSegmentNotSequentialException extends BusinessException {

    public SplitSegmentNotSequentialException() {
        super(RunningRecordErrorCode.SPLIT_SEGMENT_NOT_SEQUENTIAL);
    }
}
