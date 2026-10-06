package com.runiverse.running_service.domain.scheduling.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ScheduledJobErrorCode;

public class InvalidJobTargetException extends BusinessException {

    public InvalidJobTargetException() {
        super(ScheduledJobErrorCode.INVALID_JOB_TARGET);
    }
}
