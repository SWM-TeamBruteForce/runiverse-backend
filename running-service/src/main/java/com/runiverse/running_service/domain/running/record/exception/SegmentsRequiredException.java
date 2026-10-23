package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class SegmentsRequiredException extends BusinessException {

    public SegmentsRequiredException() {
        super(RunningRecordErrorCode.SEGMENTS_REQUIRED);
    }
}
