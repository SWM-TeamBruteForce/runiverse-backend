package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class SplitSegmentNotFoundException extends BusinessException {

    public SplitSegmentNotFoundException() {
        super(RunningRecordErrorCode.SPLIT_SEGMENT_NOT_FOUND);
    }
}
