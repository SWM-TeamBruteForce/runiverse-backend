package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class InvalidSplitNumberException extends BusinessException {

    public InvalidSplitNumberException() {
        super(RunningRecordErrorCode.INVALID_SPLIT_NUMBER);
    }
}
