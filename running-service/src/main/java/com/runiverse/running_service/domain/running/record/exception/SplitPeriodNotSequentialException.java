package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class SplitPeriodNotSequentialException extends BusinessException {

    public SplitPeriodNotSequentialException() {
        super(RunningRecordErrorCode.SPLIT_PERIOD_NOT_SEQUENTIAL);
    }
}
