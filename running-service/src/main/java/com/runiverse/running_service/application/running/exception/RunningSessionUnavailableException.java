package com.runiverse.running_service.application.running.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;

public class RunningSessionUnavailableException extends BusinessException {

    public RunningSessionUnavailableException() {
        super(RunningErrorCode.RUNNING_SESSION_UNAVAILABLE);
    }
}
