package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class PaceOutOfRangeException extends BusinessException {

    public PaceOutOfRangeException() {
        super(RunningMetricErrorCode.PACE_OUT_OF_RANGE);
    }
}
