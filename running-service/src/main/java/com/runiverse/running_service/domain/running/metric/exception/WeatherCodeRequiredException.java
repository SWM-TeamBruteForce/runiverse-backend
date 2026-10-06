package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class WeatherCodeRequiredException extends BusinessException {

    public WeatherCodeRequiredException() {
        super(RunningMetricErrorCode.WEATHER_CODE_REQUIRED);
    }
}
