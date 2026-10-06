package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class InvalidSplitIdException extends BusinessException {

    public InvalidSplitIdException() {
        super(RunningRecordErrorCode.INVALID_SPLIT_ID);
    }
}
