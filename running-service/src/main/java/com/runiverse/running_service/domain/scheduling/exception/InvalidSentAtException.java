package com.runiverse.running_service.domain.scheduling.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.ScheduledJobErrorCode;

public class InvalidSentAtException extends BusinessException {

    public InvalidSentAtException() {
        super(ScheduledJobErrorCode.INVALID_SENT_AT);
    }
}
