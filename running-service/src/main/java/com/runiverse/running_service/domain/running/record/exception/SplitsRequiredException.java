package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class SplitsRequiredException extends BusinessException {

    public SplitsRequiredException() {
        super(RunningRecordErrorCode.SPLITS_REQUIRED);
    }
}
