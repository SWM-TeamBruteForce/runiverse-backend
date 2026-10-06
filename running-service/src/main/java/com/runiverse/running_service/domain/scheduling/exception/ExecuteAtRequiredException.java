package com.runiverse.running_service.domain.scheduling.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ScheduledJobErrorCode;

public class ExecuteAtRequiredException extends BusinessException {

    public ExecuteAtRequiredException() {
        super(ScheduledJobErrorCode.EXECUTE_AT_REQUIRED);
    }
}
