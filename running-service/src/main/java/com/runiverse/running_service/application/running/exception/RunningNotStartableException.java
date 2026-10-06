package com.runiverse.running_service.application.running.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;

public class RunningNotStartableException extends BusinessException {

    public RunningNotStartableException() {
        super(RunningErrorCode.INVALID_ROOM_STATE);
    }
}
