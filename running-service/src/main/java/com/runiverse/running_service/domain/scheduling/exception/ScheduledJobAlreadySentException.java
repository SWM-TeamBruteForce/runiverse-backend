package com.runiverse.running_service.domain.scheduling.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ScheduledJobErrorCode;

public class ScheduledJobAlreadySentException extends BusinessException {

    public ScheduledJobAlreadySentException() {
        super(ScheduledJobErrorCode.SCHEDULED_JOB_ALREADY_SENT);
    }
}
