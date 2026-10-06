package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class TemperatureRequiredException extends BusinessException {

    public TemperatureRequiredException() {
        super(RunningMetricErrorCode.TEMPERATURE_REQUIRED);
    }
}
