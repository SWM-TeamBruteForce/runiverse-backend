package com.runiverse.running_service.application.running.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;

public class PauseSegmentDecreasedException extends BusinessException {

    public PauseSegmentDecreasedException() {
        super(RunningErrorCode.PAUSE_SEGMENT_DECREASED);
    }
}
