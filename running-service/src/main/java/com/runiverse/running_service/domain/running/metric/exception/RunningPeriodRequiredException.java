package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class RunningPeriodRequiredException extends BusinessException {

    public RunningPeriodRequiredException() {
        super(RunningMetricErrorCode.RUNNING_PERIOD_REQUIRED);
    }
}
