package com.runiverse.running_service.domain.common.exception;

public class InvalidRunningRecordIdException extends BusinessException {

    public InvalidRunningRecordIdException() {
        super(RunningRecordErrorCode.INVALID_RUNNING_RECORD_ID);
    }
}
