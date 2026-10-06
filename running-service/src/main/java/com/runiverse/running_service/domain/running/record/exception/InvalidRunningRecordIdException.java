package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class InvalidRunningRecordIdException extends BusinessException {

    public InvalidRunningRecordIdException() {
        super(RunningRecordErrorCode.INVALID_RUNNING_RECORD_ID);
    }
}
