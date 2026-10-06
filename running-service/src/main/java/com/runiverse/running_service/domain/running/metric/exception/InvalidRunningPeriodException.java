package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class InvalidRunningPeriodException extends BusinessException {

    public InvalidRunningPeriodException() {
        super(RunningMetricErrorCode.INVALID_RUNNING_PERIOD);
    }
}
