package com.runiverse.running_service.application.running.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;

public class AlreadyRunningException extends BusinessException {

    public AlreadyRunningException() {
        super(RunningErrorCode.RUNNING_ALREADY_IN_PROGRESS);
    }
}
