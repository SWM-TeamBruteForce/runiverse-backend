package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class SplitNumberNotSequentialException extends BusinessException {

    public SplitNumberNotSequentialException() {
        super(RunningRecordErrorCode.SPLIT_NUMBER_NOT_SEQUENTIAL);
    }
}
