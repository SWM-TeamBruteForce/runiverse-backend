package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class SplitPeriodOutOfRecordException extends BusinessException {

    public SplitPeriodOutOfRecordException() {
        super(RunningRecordErrorCode.SPLIT_PERIOD_OUT_OF_RECORD);
    }
}
