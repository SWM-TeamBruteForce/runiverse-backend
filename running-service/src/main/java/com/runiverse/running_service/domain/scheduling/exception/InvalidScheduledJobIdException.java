package com.runiverse.running_service.domain.scheduling.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ScheduledJobErrorCode;

public class InvalidScheduledJobIdException extends BusinessException {

    public InvalidScheduledJobIdException() {
        super(ScheduledJobErrorCode.INVALID_SCHEDULED_JOB_ID);
    }
}
