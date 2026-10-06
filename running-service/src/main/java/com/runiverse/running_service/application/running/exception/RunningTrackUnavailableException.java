package com.runiverse.running_service.application.running.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;

public class RunningTrackUnavailableException extends BusinessException {

    public RunningTrackUnavailableException() {
        super(RunningErrorCode.RUNNING_TRACK_UNAVAILABLE);
    }
}
