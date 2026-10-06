package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class ElapsedTimeOutOfRangeException extends BusinessException {

    public ElapsedTimeOutOfRangeException() {
        super(RunningMetricErrorCode.ELAPSED_TIME_OUT_OF_RANGE);
    }
}
