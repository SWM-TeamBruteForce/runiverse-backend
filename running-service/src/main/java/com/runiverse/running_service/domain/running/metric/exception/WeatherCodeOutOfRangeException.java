package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class WeatherCodeOutOfRangeException extends BusinessException {

    public WeatherCodeOutOfRangeException() {
        super(RunningMetricErrorCode.WEATHER_CODE_OUT_OF_RANGE);
    }
}
